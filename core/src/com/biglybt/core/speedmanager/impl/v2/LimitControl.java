package com.biglybt.core.speedmanager.impl.v2;

/*
 * Created on Jul 12, 2007
 * Created by Alan Snyder
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

public interface LimitControl
{
    SMUpdate adjust(float amount);

    void updateLimits(int upMax, int upMin, int downMax, int downMin);

    void updateSeedSettings(float downloadModeUsed);

    void updateStatus(int currUpLimit, SaturatedMode uploadUsage,
                             int currDownLimit, SaturatedMode downloadUsage,
                             TransferMode transferMode);

    void setDownloadUnlimitedMode(boolean isUnlimited);

    boolean isDownloadUnlimitedMode();
}
