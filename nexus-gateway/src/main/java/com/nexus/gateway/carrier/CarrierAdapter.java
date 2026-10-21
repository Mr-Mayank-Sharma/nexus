package com.nexus.gateway.carrier;

import com.nexus.gateway.account.Accounts.Account;

import java.util.List;

/** One carrier behind the gateway's single shipping API. A return label is a label with {@code isReturn}. */
public interface CarrierAdapter {

    String code();

    List<Shipping.Quote> rates(Account account, Shipping.RateRequest request);

    Shipping.Label label(Account account, Shipping.LabelRequest request);

    boolean voidLabel(Account account, String carrierShipmentId, String trackingNumber);

    Shipping.AddressCheck validate(Account account, Shipping.Address address);

    Shipping.Tracking track(Account account, String trackingNumber);
}
