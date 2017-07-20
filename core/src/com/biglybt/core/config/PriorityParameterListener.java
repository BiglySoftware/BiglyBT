/*
 * Created on Jan 28, 2010
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.config;

/**
 * Instances of this will be invoked before any ParameterListeners are.
 * The purpose of this is to allow safe caching of parameter values in the light of other
 * listeners that may use the cached values and hence must be invoked after the cached value
 * has been updated. Therefore only ever use this type of listener for this purpose!
 * @author Paul
 *
 */
public interface
PriorityParameterListener
	extends ParameterListener
{
}
