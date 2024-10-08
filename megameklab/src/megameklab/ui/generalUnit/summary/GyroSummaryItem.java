/*
 * Copyright (c) 2023 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMekLab.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MegaMek. If not, see <http://www.gnu.org/licenses/>.
 */
package megameklab.ui.generalUnit.summary;

import megamek.common.Entity;
import megamek.common.Mek;
import megamek.common.verifier.TestMek;
import megameklab.util.UnitUtil;

public class GyroSummaryItem extends AbstractSummaryItem {

    @Override
    public String getName() {
        return "Gyro";
    }

    @Override
    public void refresh(Entity entity) {
        if ((entity instanceof Mek) && (entity.getGyroType() != Mek.GYRO_NONE)) {
            Mek mek = (Mek) entity;
            availabilityLabel.setText(mek.getGyroTechAdvancement().getFullRatingName(entity.isClan()));
            TestMek testMek = (TestMek) UnitUtil.getEntityVerifier(entity);
            weightLabel.setText(formatWeight(testMek.getWeightGyro(), entity));
            critLabel.setText(formatCrits(getGyroCrits(entity)));
        } else {
            availabilityLabel.setText("");
            weightLabel.setText("");
            critLabel.setText("");
        }
    }

    private int getGyroCrits(Entity entity) {
        switch(entity.getGyroType()) {
            case Mek.GYRO_COMPACT:
                return 2;
            case Mek.GYRO_XL:
                return 6;
            default:
                return 4;
        }
    }
}
