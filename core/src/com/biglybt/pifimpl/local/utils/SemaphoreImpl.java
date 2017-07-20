/*
 * File    : SemaphoreImpl.java
 * Created : 24-Mar-2004
 * By      : parg
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

package com.biglybt.pifimpl.local.utils;

/**
 * @author parg
 *
 */

import com.biglybt.core.util.AESemaphore;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.utils.Semaphore;

public class
SemaphoreImpl
	implements Semaphore
{
	private static long	next_sem_id;

	private AESemaphore		sem;

	protected
	SemaphoreImpl(
		PluginInterface		pi )
	{
		synchronized( SemaphoreImpl.class ){

			sem	= new AESemaphore("Plugin " + pi.getPluginID() + ":" + next_sem_id++ );
		}
	}

	@Override
	public void
	reserve()
	{
		sem.reserve();
	}

	@Override
	public boolean
	reserveIfAvailable()
	{
		return( sem.reserveIfAvailable());
	}

	@Override
	public boolean
	reserve(
		long	timeout_millis )
	{
		return( sem.reserve( timeout_millis ));
	}

	@Override
	public void
	release()
	{
		sem.release();
	}

	@Override
	public void
	releaseAllWaiters() {
		sem.releaseAllWaiters();
	}

}
