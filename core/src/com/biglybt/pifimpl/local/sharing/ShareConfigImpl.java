/*
 * File    : ShareConfigImpl.java
 * Created : 31-Dec-2003
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

package com.biglybt.pifimpl.local.sharing;

/**
 * @author parg
 *
 */

import java.util.*;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.sharing.ShareException;
import com.biglybt.pif.sharing.ShareResource;

public class
ShareConfigImpl
{
	protected ShareManagerImpl		manager;

	protected int					suspend_level;

	protected boolean				save_outstanding;

	protected AEMonitor				this_mon	= new AEMonitor( "ShareConfig" );

	protected void
	loadConfig(
		ShareManagerImpl	_manager )
	{
		manager	= _manager;

		try{

			Map map = FileUtil.readResilientConfigFile("sharing.config");

			List resources = (List) map.get("resources");

			if (resources == null){

				return;
			}

			Iterator  iter = resources.iterator();

			while (iter.hasNext()) {

				Map r_map = (Map) iter.next();

				manager.deserialiseResource( r_map );
			}

		}catch (Exception e) {

			Debug.printStackTrace( e );
		}
	}

	protected void
	saveConfig()

		throws ShareException
	{
		try{
			this_mon.enter();

			if ( suspend_level > 0 ){

				save_outstanding = true;

				return;
			}

			Map map = new HashMap();

			List list = new ArrayList();

			map.put("resources", list);

			ShareResource[]	shares = manager.getShares();

			for (int i=0;i<shares.length;i++){

				Map	m = new HashMap();

				((ShareResourceImpl)shares[i]).serialiseResource( m );

				list.add( m );
			}

			FileUtil.writeResilientConfigFile("sharing.config", map);

		}finally{

			this_mon.exit();
		}
	}

	protected void
	suspendSaving()
	{
		try{
			this_mon.enter();

			suspend_level++;

		}finally{

			this_mon.exit();
		}
	}

	protected void
	resumeSaving()
		throws ShareException
	{
		try{
			this_mon.enter();

			suspend_level--;

			if ( suspend_level == 0 && save_outstanding ){

				save_outstanding	= false;

				saveConfig();
			}
		}finally{

			this_mon.exit();
		}
	}
}

