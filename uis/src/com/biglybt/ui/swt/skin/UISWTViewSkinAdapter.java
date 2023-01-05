/*
 * Created on Dec 1, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.ui.swt.skin;

import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;

import com.biglybt.core.util.CopyOnWriteMap;


public class
UISWTViewSkinAdapter
{
	private final String		skin_folder;
	private final String		skin_file;
	private final String		wrapper_id;
	private final String		target_id;

	private CopyOnWriteMap<UISWTView,ViewHolder> subviews = new CopyOnWriteMap<>();

	public
	UISWTViewSkinAdapter(
		String		_skin_folder,
		String		_skin_file,
		String		_wrapper_id,
		String		_target_id )
	{
		skin_folder		= _skin_folder;
		skin_file		= _skin_file;
		wrapper_id		= _wrapper_id;
		target_id		= _target_id;
	}

	public boolean
	eventOccurred(
		UISWTViewEvent event )
	{
		UISWTView 	currentView = event.getView();

		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:{

				SWTSkin skin = SWTSkinFactory.getNonPersistentInstance(
						getClass().getClassLoader(),
						skin_folder,
						skin_file );

				subviews.put(currentView, new ViewHolder( currentView, skin ));

				event.getView().setDestroyOnDeactivate(false);

				break;
			}
			case UISWTViewEvent.TYPE_INITIALIZE:{

				ViewHolder subview = subviews.get(currentView);

				if ( subview != null ){

					subview.initialise((Composite)event.getData(), currentView.getDataSource());
				}

				break;
			}
			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:{

				ViewHolder subview = subviews.get(currentView);

				if ( subview != null ){

					subview.setDataSource( event.getData());
				}

				break;
			}

			case UISWTViewEvent.TYPE_FOCUSLOST:{

				ViewHolder subview = subviews.get(currentView);

				if ( subview != null ){

					subview.focusLost();
				}

				break;
			}
			case UISWTViewEvent.TYPE_FOCUSGAINED:{

				ViewHolder subview = subviews.get(currentView);

				if ( subview != null ){

					subview.focusGained();
				}

				break;
			}
			case UISWTViewEvent.TYPE_DESTROY:{

				ViewHolder subview = subviews.remove(currentView);

				if ( subview != null ){

					subview.destroy();
				}

				break;
			}
		}

		return( true );
	}

	private class
	ViewHolder
	{
		private final UISWTView		view;
		private final SWTSkin		skin;

		private	SWTSkinObject		so;

		private
		ViewHolder(
			UISWTView 	_view,
			SWTSkin		_skin  )
		{
			view	= _view;
			skin	= _skin;
		}

		protected void
		initialise(
			Composite		parent,
			Object			data_source )
		{
			Composite skin_area = new Composite( parent, SWT.NULL );

			skin_area.setLayout( new FormLayout());

			skin_area.setLayoutData( new GridData( GridData.FILL_BOTH ));

			skin.initialize( skin_area, wrapper_id );

			so = skin.getSkinObjectByID( target_id );

			so.triggerListeners( SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED, data_source );

			so.setVisible( true );

			skin.layout();
		}

		protected void
		setDataSource(
			Object		data_source )
		{
			if ( so != null ){

				so.triggerListeners( SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED, data_source );
			}
		}

		public void
		focusGained()
		{
			if ( so != null ){

				so.setVisible( true );
			}
		}

		public void
		focusLost()
		{
			if ( so != null ){

				so.setVisible( false );
			}
		}

		protected void
		destroy()
		{
			if ( so != null ){

				so.dispose();

				skin.removeSkinObject( so );

				so = null;
			}
		}
	}
}
