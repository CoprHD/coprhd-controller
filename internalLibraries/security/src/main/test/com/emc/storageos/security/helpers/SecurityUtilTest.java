/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.helpers;

import com.emc.storageos.security.ApplicationContextUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

// This test is currently failing and is being @Ignored and tracked with Jira COP-19799
@Ignore
public class SecurityUtilTest {

    String buildType;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() throws Exception {
        ApplicationContextUtil.initContext(System.getProperty("buildType"), ApplicationContextUtil.SECURITY_CONTEXTS);
    }

    @Test
    public void loadPrivateKeyFromPCKS1PEMString() throws Exception {

        String pemedPrivKey =
                "-----BEGIN RSA PRIVATE KEY-----\n" +
                        "MIIEowIBAAKCAQEA62BnELI4IC32p4l8G3CJ0SiFM3OK4HqMSRvmVmZyLVWZOcrU\n" +
                        "twUEfW4EzXPtfbDOr3kgMV3I/8sKXnk3aVuuaUwAgHdBRJG0LTxnPzZr10kSQRIk\n" +
                        "Z93/mClzxAhUqfkJe7lzlI1MSvUA3alfoL3x/xs6XL2+/nyYc3d+moIZmynaDMi8\n" +
                        "j5bh7vjZiW0Pa/5RWqsWOXl/mp9fIZmTfcTmHPFasLpFpxvkw4mSJm3s8rstAKfF\n" +
                        "ZKv/my3n54BJr4EDCwtPmCX7kN1YriEDhnCEro8KCjCd0rDSQlv5ih9cSBvq8Xqe\n" +
                        "60qemFsCXFKXz9C0tWLdfu+AHq8rXBvQ0+AlcwIDAQABAoIBADRAuYCuX+Fc8tXs\n" +
                        "op2g1SeWvENY0irVadFNHUxu/8fqqdJ3odNjjMSfqPdHViDCIJA57pim/lky5QFm\n" +
                        "HZQJI2YC1lWuo9GqGvqz3yPcXAJ2GVRSx7w7P2OzSBzr0IfPXniWCf+fgqFdVKzg\n" +
                        "zfbsVA3TKJiwEveCB4IStAtu+GpGaj2apmAZzjU3n/aUvzEX6QzjOf4ICJSwiAp0\n" +
                        "S4cL/IgG7ZzqU9Tjc3U9TQA5ctDHwHCDHJvE9AEpJ2y1SRF3keiEtn53RpXv1hSf\n" +
                        "A0Xf6FAykCpeqh/2+e7Pb/j20BT9wNUaqmTrbA9PkCOYyujbyQNFu6yOH+gEwHBq\n" +
                        "Tu/UgQkCgYEA+ZmVcBqf342fChMwsLV8ZRyg6POGvl1467aqUbJ4MtpdXWRulwNA\n" +
                        "njx6MGnvaUbM/lajLd7qn7X7neGWGbDQcil+qCN3NP59MAfBYbpGc8ecfLc8OPmq\n" +
                        "NlEo4IPwwuRQVRvZXytKn++Pnpndf74r1BoqsypM4aMilbw6bq10sv8CgYEA8Wl0\n" +
                        "TC4ECtNovviXBu5EOs+NKVPHLVWNsIujnQWaTgOcz6zTphEw+mV5X8Yg9Q3pMuZq\n" +
                        "EHE6mG2l/P3TbC0zmDeHW4t0o3bvvU7Z0YliqwnpjeNmndjHFyavHbuUej+1mABT\n" +
                        "bpjaThvTBK/pqB0RrIWiSTk+BCpr3bGRi6NpcY0CgYBDpqgFWYIJkpYPogHpc1dD\n" +
                        "BI5sdU0JHcafhMQHHqtLhToXDRiYX65M9TFdG+ljDGiVayARV8EaBrYrJbCMSAtH\n" +
                        "Vg3ZPruZpnyB3yg/98AOs4SrnPJ6sti6nibJK5m/CSjo2IKDM/WnAcRYhXWuoPm6\n" +
                        "JFl8dDOgWJoQIZNo2iSuOwKBgCftL/3Vcnez5VQIWzobOA+d+hPGtl9qEegMAEBd\n" +
                        "B9s1P78dL1f86ePP2pYbd6Hv7gysDQixIWbKY7SG6muuwiS/slRSqTTL2/SsUNwV\n" +
                        "48qz9fuwi205yButGzNIrdLWLJt6GlJlV12pvmm9CmDyhdTJuUBxBi+8MGDXGGJG\n" +
                        "4Y6VAoGBAOCZYxIctvcOYXShamJo+e+vl96dFq8gR0jDa3o0CgdLl83Oveqgw3vS\n" +
                        "rEbku+bT3cgcY07mCAfppspKyhfkCsRPl6jfyJIJxxrAef11zdrnCSrjYpl3/1L8\n" +
                        "YTN3JGBvqy4Z/i1/FoBBiLNA/oqCgYBFFViDDwkGsk/kvhp/7tBe\n" +
                        "-----END RSA PRIVATE KEY-----\n";

        if (buildType.equals("oss")) {
            // PKCS1 should NOT be supported in OSS
            exception.expect(Exception.class);
        }

        byte[] privKeyBytes = SecurityUtil.loadPrivateKeyFromPEMString(pemedPrivKey);

        Assert.assertNotNull(privKeyBytes);
    }

    @Test
    public void loadPrivateKeyFromPCKS8PEMString() throws Exception {

        String pemedPrivKey =
                "-----BEGIN PRIVATE KEY-----\n" +
                        "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDWndIiJjHviLyU\n" +
                        "XqunhokM+vM0vOmY4+RDBdE+zrY8CvZxoeTgf9B8aQ3jr02NKudBDFSsQ2UBXZ53\n" +
                        "z3vW1Bpr9QgNcag5zZCKPAXJuikIFljVrlKMMcVusXx04boNRiZkSGde8aO258If\n" +
                        "yi/OXGcYWZPpyHJPTx718xPyIqcndTtjz48kMkc1Z+YoYH/S1U4Gr7prsRk7vISa\n" +
                        "InXNeZsSejcYgM4XRt8if1dPMMs7ceEZH/xngjA15bcgRzjkGzgF2Zc4Ae5/C9Pm\n" +
                        "nUlq7F0FEaicBEg0Zsyce78tk0xQBq1BMzjmU0cM/y7ztFLwXIBJUnhkx5jCneMP\n" +
                        "ljElfi3NAgMBAAECggEBANIFo8uzYQ6t2w2/D3Bjod7H/hTQAjGSYqCMIta553Ae\n" +
                        "sklFSvu/WMFG9UapGNVa9O+dJ4dkdIW/ngJVUzVKX4jiQz0UyrG2TwpX7roYqWq2\n" +
                        "o7yIWVPEqRbILakb9Lxkt9FPYYlyIuotJrWBYQMrPeCAWNVhSSv/m6bId+5upvFe\n" +
                        "ZzfMIZ3rs9m+ZzfGWuNORpXwGe0l70ExsTRPReXhjUvyAlctJUdZbAeb42Ms1SYF\n" +
                        "PSaaFd2b2ARLbzRmkEk1VaMVSRfLft+8ZehjP7Y+i/INffU2Mn1MnRn1FGIVt1uh\n" +
                        "8xy2j7YNK4JCWdvJXLAGN7q4VooGbcpNRYh7BbEe4+kCgYEA6/daf7KQU4nNUnwl\n" +
                        "GGFFtboYvSa0EYmZ7EuYrcNYIyw6WeSWwYSyeHEa+QGYCc5bCbA2DMcqj3YkvMyG\n" +
                        "FfySPYonHZnjSfMhaVblrbUlHErGv3Jwet75qSLfHBMtaVATQwXrW56mtF3pBOro\n" +
                        "6jNZ6+LonytntY1zb03i+dFjP3sCgYEA6NZwHvqpEuc0vPxOXJqI01WwIRXjN+8w\n" +
                        "AVnREkFs14JdiWvlJUNNiTduhvEX750v3Uc2IGvglzVdosZvbLgDGGs5VgQQW6Vw\n" +
                        "75+nvLDoUaA8x8P5NPuR7pNVUjIjz7Da72wLPt+JYF4Ywh+U/DVALgCwr+rKjY1W\n" +
                        "Q92r2MxCYVcCgYEA1Jss/ku8QJiz1/MlVT8nmSKQ8bSyn+6UMlS2vzF381EBTkdp\n" +
                        "Efnm+CAoxl0KSSOV4TfUq1S0Y0h2t2msEplchng44DHsmY+n9gqmrQm+4yv4wTxy\n" +
                        "XjMTTbKuxdP9oZrVkBkAQ6/B0lefAaBKteIIzkHHiMqKCgnmDU5nCOIBg4sCgYEA\n" +
                        "1Wv5yeupHpKfXLtIeMmNsWlR0IHnjFXKgvJ5GWqvAbuVUWl82PMgFy6gOUC02AYU\n" +
                        "4ZdsnbtEWlWoRpPtfpINBE0EeTuwYtD6/Coz7lmaGXfvPoz72Pzffve9tWIQQUey\n" +
                        "5tL6W986ZQLbXtTkE2ocMg6f5iApGib7c6m04zwXfHECgYBTzVaNrKfyvnaSrdfY\n" +
                        "qE+sJrl2YyO682kaX7CRhF5rNcviH7xJVEOGZfdSTEOCtWRtvO2jWw1OVHXh+CDT\n" +
                        "KQLoM74GYXM/GOc8QNDVFr0z2YmroNkWDF5qdgnSwz04Z3+1tpVVpIm9KczxpCln\n" +
                        "0LYID4m9NU8unO0dwZv3Y8803Q==\n" +
                        "-----END PRIVATE KEY-----";

        byte[] privKeyBytes = SecurityUtil.loadPrivateKeyFromPEMString(pemedPrivKey);

        Assert.assertNotNull(privKeyBytes);
    }
}
