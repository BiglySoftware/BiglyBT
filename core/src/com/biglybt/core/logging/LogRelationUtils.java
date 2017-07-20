/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
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
 *
 */

package com.biglybt.core.logging;

/**
 * @author TuxPaper
 * @created Mar 19, 2007
 *
 */
public class LogRelationUtils
{
	public static Object queryForClass(Object[] objects, Class cla) {
		if (objects == null) {
			return null;
		}

		// Pass 1: Quick check if class is in objects
		for (int i = 0; i < objects.length; i++) {
			Object object = objects[i];
			if (cla.isInstance(object)) {
				return object;
			}
		}

		// Pass 2: check LogRelations
		for (int i = 0; i < objects.length; i++) {
			Object object = objects[i];
			if (object instanceof LogRelation) {
				LogRelation logRelation = (LogRelation)object;
				Object answer = logRelation.queryForClass(cla);
				if (answer != null) {
					return answer;
				}
			}
		}

		return null;
	}
}
