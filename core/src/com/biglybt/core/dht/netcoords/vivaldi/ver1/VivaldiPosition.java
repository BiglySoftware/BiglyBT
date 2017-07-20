/*
 * Created on 22 juin 2005
 * Created by Olivier Chalouhi
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.core.dht.netcoords.vivaldi.ver1;

import com.biglybt.core.dht.netcoords.DHTNetworkPosition;

public interface
VivaldiPosition
	extends DHTNetworkPosition
{

  final static int CONVERGE_EVERY = 5;
  final static float CONVERGE_FACTOR = 50f;

  // controlling parameters
  public final static float ERROR_MIN = 0.1f;

  public Coordinates getCoordinates();

  public float getErrorEstimate();

  public void  setErrorEstimate(float error);

  public void update(float rtt,Coordinates coordinates,float error);

  public void update(float rtt, float[] serialised_data );

  public float estimateRTT(Coordinates coordinates);

  	// serialisation stuff

  public static final int	FLOAT_ARRAY_SIZE	= 4;	// size of float-serialisation array size

  public float[] toFloatArray();

  public void fromFloatArray( float[] data );
}
