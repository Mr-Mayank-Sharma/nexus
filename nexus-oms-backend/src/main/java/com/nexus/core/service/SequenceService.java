package com.nexus.core.service;

import com.nexus.core.CoreTransactional;
import com.nexus.core.tenancy.TenantScope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out ids from {@code sequence_value_item}, the OFBiz/Moqui way: each server reserves a bank of numbers in a
 * short transaction of its own (so a rolled-back business transaction never returns numbers, and two servers never
 * get the same one), then serves ids from memory. Ids are readable numbers starting at 10000, for example "10042".
 * Ids are unique, not gap-free: a restart abandons the rest of a bank. Each core database (one per company) has its own
 * sequences, so banks are kept per database.
 */
@Service("coreSequenceService")
public class SequenceService {

    static final long FIRST_ID = 10000;

    private final JdbcTemplate jdbc;
    private final TenantScope tenantScope;
    private final TransactionTemplate ownTransaction;
    private final int bankSize;
    /** Banks by database key and sequence name. */
    private final Map<BankKey, Bank> banks = new ConcurrentHashMap<>();

    public SequenceService(@Qualifier(CoreTransactional.JDBC) JdbcTemplate jdbc, @Qualifier(CoreTransactional.MANAGER) PlatformTransactionManager transactionManager,
                           TenantScope tenantScope, @Value("${nexus.sequence.bank-size:50}") int bankSize) {
        this.jdbc = jdbc;
        this.tenantScope = tenantScope;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        if (bankSize < 1) throw new IllegalArgumentException("nexus.sequence.bank-size must be at least 1");
        this.bankSize = bankSize;
    }

    /** The next id for a sequence, usually the entity name: {@code nextId("OrderHeader")}. */
    public String nextId(String seqName) {
        return Long.toString(nextLong(seqName));
    }

    public long nextLong(String seqName) {
        Bank bank = banks.computeIfAbsent(new BankKey(tenantScope.currentDatabaseKey(), seqName), key -> new Bank());
        synchronized (bank) {
            if (bank.next >= bank.end) {
                bank.next = reserve(seqName);
                bank.end = bank.next + bankSize;
            }
            return bank.next++;
        }
    }

    /** Detail-row sequence ids, as OFBiz writes them: 1 becomes "00001". */
    public static String seqId(int n) {
        if (n < 1) throw new IllegalArgumentException("Sequence ids start at 1");
        return String.format("%05d", n);
    }

    /** Reserves the next bank and returns its first number. */
    private long reserve(String seqName) {
        for (int attempt = 0; attempt < 5; attempt++) {
            Long first = ownTransaction.execute(status -> {
                List<Long> current = jdbc.queryForList(
                        "SELECT seq_num FROM sequence_value_item WHERE seq_name = ? FOR UPDATE", Long.class, seqName);
                if (current.isEmpty()) return null;
                long start = current.get(0);
                jdbc.update("UPDATE sequence_value_item SET seq_num = ?, last_updated_stamp = CURRENT_TIMESTAMP, "
                        + "last_updated_tx_stamp = CURRENT_TIMESTAMP WHERE seq_name = ?", start + bankSize, seqName);
                return start;
            });
            if (first != null) return first;
            try {
                ownTransaction.executeWithoutResult(status -> jdbc.update(
                        "INSERT INTO sequence_value_item (seq_name, seq_num, last_updated_stamp, last_updated_tx_stamp, created_stamp, created_tx_stamp) "
                                + "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", seqName, FIRST_ID + bankSize));
                return FIRST_ID;
            } catch (DuplicateKeyException another) {
                // another server created the row first; read it again
            }
        }
        throw new IllegalStateException("Could not reserve ids for sequence " + seqName);
    }

    private record BankKey(String databaseKey, String seqName) {
    }

    private static final class Bank {
        long next;
        long end;
    }
}
