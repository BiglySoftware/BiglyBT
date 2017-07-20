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

package com.biglybt.core.util;

/**
 * @author TuxPaper
 * @created Mar 22, 2007
 *
 */
public abstract class AERunnableObject
	implements Runnable
{
	private Object[] returnValueObject;

	private AESemaphore sem;

	private String id = "AEReturningRunnable";

	@Override
	public void run() {
		try {
			Object o = runSupport();
			if (returnValueObject != null && returnValueObject.length > 0) {
				returnValueObject[0] = o;
			}
		} catch (Throwable e) {
			Debug.out(id, e);
		} finally {
			if (sem != null) {
				sem.releaseForever();
			}
		}
	}

	public void setupReturn(String ID, Object[] returnValueObject, AESemaphore sem) {
		id = ID;
		this.returnValueObject = returnValueObject;
		this.sem = sem;
	}

	public abstract Object runSupport();
}
