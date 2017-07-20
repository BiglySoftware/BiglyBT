/*
 * Created on 07-May-2004
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

package com.biglybt.pifimpl.local.update;

/**
 * @author parg
 *
 */

import java.io.InputStream;
import java.util.Iterator;

import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.update.Update;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pif.update.UpdateException;
import com.biglybt.pif.update.UpdateListener;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;

public class
UpdateImpl
	implements Update
{
	private UpdateCheckInstanceImpl	instance;
	private UpdatableComponentImpl	component;
	private String					name;
	private String[]				description;
	private String					relative_url_base	= "";
	private String					old_version;
	private String					new_version;
	private ResourceDownloader[]	downloaders;
	private boolean					mandatory;
	private int						restart_required;
	private String 					description_url;

	private Object					user_object;

	private CopyOnWriteList			listeners = new CopyOnWriteList();
	private volatile boolean		cancelled;
	private volatile boolean		complete;
	private volatile boolean		succeeded;

	protected
	UpdateImpl(
		UpdateCheckInstanceImpl	_instance,
		UpdatableComponentImpl	_component,
		String					_name,
		String[]				_desc,
		String					_old_version,
		String					_new_version,
		ResourceDownloader[]	_downloaders,
		boolean					_mandatory,
		int						_restart_required )
	{
		instance			= _instance;
		component			= _component;
		name				= _name;
		description			= _desc;
		old_version			= _old_version;
		new_version			= _new_version;
		downloaders			= _downloaders;
		mandatory			= _mandatory;
		restart_required	= _restart_required;

		/*
		System.out.println( "Update:" + name + "/" + new_version + ", mand=" + mandatory + ", restart = " + restart_required  );

		for (int i=0;i<description.length;i++){

			System.out.println( description[i]);
		}

		for (int i=0;i<downloaders.length;i++){

			try{
				System.out.println( "  size:" + downloaders[i].getSize());
			}catch( Throwable e ){

				e.printStackTrace();
			}
		}
		*/
	}

	@Override
	public UpdateCheckInstance
	getCheckInstance()
	{
		return( instance );
	}

	protected UpdatableComponentImpl
	getComponent()
	{
		return( component );
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public String[]
	getDescription()
	{
		return( description );
	}

	@Override
	public String
	getRelativeURLBase()
	{
		return( relative_url_base );
	}

	@Override
	public void
	setRelativeURLBase(
		String	base )
	{
		relative_url_base = base;
	}

	// @see com.biglybt.pif.update.Update#getDesciptionURL()
	@Override
	public String
	getDesciptionURL()
	{
		return description_url;
	}

	// @see com.biglybt.pif.update.Update#setDescriptionURL(java.lang.String)
	@Override
	public void
	setDescriptionURL(String url)
	{
		description_url = url;
	}

	@Override
	public String
	getOldVersion()
	{
		return( old_version );
	}

	@Override
	public String
	getNewVersion()
	{
		return( new_version );
	}

	@Override
	public ResourceDownloader[]
	getDownloaders()
	{
		return( downloaders );
	}

	@Override
	public boolean
	isMandatory()
	{
		return( mandatory );
	}

	@Override
	public void
	setRestartRequired(
		int	_restart_required )
	{
		restart_required	= _restart_required;
	}

	@Override
	public int
	getRestartRequired()
	{
		return( restart_required );
	}

	@Override
	public void
	setUserObject(
		Object		obj )
	{
		user_object	= obj;
	}

	@Override
	public Object
	getUserObject()
	{
		return( user_object );
	}

	@Override
	public void
	cancel()
	{
		cancelled = true;

		for (int i=0;i<downloaders.length;i++){

			try{
				downloaders[i].cancel();

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((UpdateListener)it.next()).cancelled( this );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	complete(
		boolean	success )
	{
		complete	= true;
		succeeded	= success;

		Iterator it = listeners.iterator();

		for (int i=0;i<listeners.size();i++){

			try{
				((UpdateListener)it.next()).complete( this );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public boolean
	isCancelled()
	{
		return( cancelled );
	}

	@Override
	public boolean
	isComplete()
	{
		return( complete );
	}

	@Override
	public boolean
	wasSuccessful()
	{
		return( succeeded );
	}

	@Override
	public Object
	getDecision(
		int			decision_type,
		String		decision_name,
		String		decision_description,
		Object		decision_data )
	{
		return( instance.getDecision(
				this, decision_type, decision_name, decision_description, decision_data ));
	}

	@Override
	public InputStream
	verifyData(
		InputStream		is,
		boolean			force )

		throws UpdateException
	{
		return(((UpdateManagerImpl)instance.getManager()).verifyData( this, is, force ));
	}

	@Override
	public void
	addListener(
		UpdateListener	l )
	{
		listeners.add( l );

		if ( cancelled ){

			l.cancelled( this );

		}else if ( complete ){

			l.complete( this );
		}
	}

	@Override
	public void
	removeListener(
		UpdateListener	l )
	{
		listeners.remove(l);
	}
}
