package com.nexus.oms.repository;

import com.nexus.oms.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
}
