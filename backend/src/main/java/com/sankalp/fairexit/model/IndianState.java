package com.sankalp.fairexit.model;

/**
 * Indian states and union territories by their short code.
 * Which states have their own deposit rules is decided by the rules table
 * (see RuleCatalog), not here, so it is stored in one place only.
 */
public enum IndianState {
    AP("Andhra Pradesh"),
    AR("Arunachal Pradesh"),
    AS("Assam"),
    BR("Bihar"),
    CG("Chhattisgarh"),
    GA("Goa"),
    GJ("Gujarat"),
    HR("Haryana"),
    HP("Himachal Pradesh"),
    JH("Jharkhand"),
    KA("Karnataka"),
    KL("Kerala"),
    MP("Madhya Pradesh"),
    MH("Maharashtra"),
    MN("Manipur"),
    ML("Meghalaya"),
    MZ("Mizoram"),
    NL("Nagaland"),
    OD("Odisha"),
    PB("Punjab"),
    RJ("Rajasthan"),
    SK("Sikkim"),
    TN("Tamil Nadu"),
    TG("Telangana"),
    TR("Tripura"),
    UP("Uttar Pradesh"),
    UK("Uttarakhand"),
    WB("West Bengal"),
    AN("Andaman and Nicobar Islands"),
    CH("Chandigarh"),
    DH("Dadra and Nagar Haveli and Daman and Diu"),
    DL("Delhi"),
    JK("Jammu and Kashmir"),
    LA("Ladakh"),
    LD("Lakshadweep"),
    PY("Puducherry");

    private final String displayName;

    IndianState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
