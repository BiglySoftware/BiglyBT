/*
 * Created on May 7, 2007
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */


package com.biglybt.core.speedmanager.impl;

import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.speedmanager.SpeedManagerPingMapper;

public interface
SpeedManagerAlgorithmProviderAdapter
{
	public SpeedManager
	getSpeedManager();

	public int
	getCurrentProtocolUploadSpeed();

	public int
	getCurrentDataUploadSpeed();

	public int
	getCurrentUploadLimit();

	public void
	setCurrentUploadLimit(
		int		bytes_per_second );

    public int
    getCurrentProtocolDownloadSpeed();

    public int
    getCurrentDataDownloadSpeed();

    public int
	getCurrentDownloadLimit();

    public void
    setCurrentDownloadLimit(int bytes_per_second);

    public SpeedManagerPingMapper
    getPingMapper();

    	/**
    	 * Creates a mapper starting from current time. Must be destroyed by calling "destroy" when
    	 * done with
    	 * @return
    	 */
    public SpeedManagerPingMapper
    createTransientPingMapper();

	public void
	log(
		String	str );
}
