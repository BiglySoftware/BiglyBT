/*
 * File    : LogRelation.java
 * Created : Nov 29, 2005
 * By      : TuxPaper
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

package com.biglybt.core.logging;

import java.util.HashSet;

/**
 * @author TuxPaper
 *
 */
public class LogRelation {
	/**
	 * A short description of what your class holds that can be printed by the logger
	 *
	 * @return
	 */
	public String getRelationText() {
		return toString();
	}

	protected final String propogatedRelationText(Object o) {
		if (o instanceof LogRelation)
			return ((LogRelation)o).getRelationText();

		return null;
	}

	/**
	 * Query this class for a reference to another class that it may hold
	 *
	 * @param c Class desired
	 * @return If found, the class desired.  Otherwise, null.
	 */
	public Object[] getQueryableInterfaces() {
		return null;
	}

	public final Object queryForClass(Class c) {
		return queryForClass(c, getQueryableInterfaces(), new HashSet<>());
	}

	private Object queryForClass(Class c, Object[] queryObjects,
			HashSet<LogRelation> stack) {
		boolean running = stack.contains(this);
		if (running || queryObjects == null)
			return null;

		try {
			stack.add(this);

			if (c.isInstance(this))
				return this;

			// Check if any of the objects are of c
			for (int i = 0; i < queryObjects.length; i++) {
				if (c.isInstance(queryObjects[i]))
					return queryObjects[i];
			}

			// Query each object that is LogRelation
			for (int i = 0; i < queryObjects.length; i++) {
				if (queryObjects[i] instanceof LogRelation) {
					Object obj = ((LogRelation) queryObjects[i]).queryForClass(c,
							((LogRelation) queryObjects[i]).getQueryableInterfaces(), stack);
					if (obj != null)
						return obj;
				}
			}

			return null;
		} finally {
			stack.remove(this);
		}
	}
}
