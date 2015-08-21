/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.pcepio;

import org.junit.Test;
import org.onosproject.pcepio.types.SharedRiskLinkGroupTlv;

import com.google.common.testing.EqualsTester;

/**
 * Test case for Shared Risk Link Group tlv.
 */
public class SharedRiskLinkGroupTlvTest {

    private int[] raw = {1 };
    private Short hLength = new Short((short) 2);
    private final SharedRiskLinkGroupTlv tlv1 = SharedRiskLinkGroupTlv.of(raw, hLength);

    private Short hLength1 = new Short((short) 2);
    private final SharedRiskLinkGroupTlv tlv2 = SharedRiskLinkGroupTlv.of(raw, hLength1);

    private int[] raw2 = {2 };
    private Short hLength2 = new Short((short) 3);
    private SharedRiskLinkGroupTlv tlv3 = SharedRiskLinkGroupTlv.of(raw2, hLength2);

    @Test
    public void basics() {
        new EqualsTester().addEqualityGroup(tlv1, tlv2).addEqualityGroup(tlv3).testEquals();
    }

}
