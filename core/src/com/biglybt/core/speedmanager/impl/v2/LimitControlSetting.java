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

public class LimitControlSetting
{

    float value;

    public LimitControlSetting(float startValue){
        value = startValue;
    }

    /**
     *
     * @return float between 0.0 and 1.0f
     */
    public float getValue(){
        return value;
    }

    public void adjust(float increment){
        value += increment;
    }

}
