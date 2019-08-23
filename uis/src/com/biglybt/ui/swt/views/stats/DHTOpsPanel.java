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
package com.biglybt.ui.swt.views.stats;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.control.DHTControlActivity;
import com.biglybt.core.dht.control.DHTControlListener;

import com.biglybt.ui.swt.views.stats.GeneralOpsPanel.Activity;
import com.biglybt.ui.swt.views.stats.GeneralOpsPanel.Node;
import com.biglybt.ui.swt.views.stats.GeneralOpsPanel.State;

public class
DHTOpsPanel
	implements DHTControlListener
{
	private final GeneralOpsPanel	gop;

	private DHT	current_dht;
	
	private Map<DHTControlActivity,ActivityHolder>		activity_map 			= new IdentityHashMap<>();
	private Map<ActivityHolder,DHTControlActivity>		activity_reverse_map 	= new IdentityHashMap<>();
	
	
	public DHTOpsPanel(Composite parent) {
		gop = new GeneralOpsPanel(parent);
	}

	public void setLayoutData(Object data) {
		gop.setLayoutData(data);
	}

	@Override
	public void
	activityChanged(
		DHTControlActivity	activity,
		int					type )
	{
		ActivityHolder	holder;
		
		synchronized( activity_map ){
			
			holder = activity_map.get( activity );
			
			if ( holder == null ){
				
				holder = new ActivityHolder( activity );
				
				activity_map.put( activity, holder );
				
				activity_reverse_map.put( holder, activity );
			}
		}
		
		gop.activityChanged( holder, type == DHTControlListener.CT_REMOVED );
	}


	public void
	refreshView(
		DHT		dht )
	{
		if ( current_dht != dht ){

			if ( current_dht != null ){

				current_dht.getControl().removeListener( this );
			}

			current_dht = dht;

			gop.reset();

			synchronized( activity_map ){
				
				activity_map.clear();
				
				activity_reverse_map.clear();
			}
			
			dht.getControl().addListener( this );
		}

		refresh();
	}

	public void setAutoAlpha(boolean autoAlpha) {
		gop.setAutoAlpha(autoAlpha);
	}
	
	public void
	setUnavailable(){
		gop.setUnavailable();
	}
	
	public void
	setFilter(
		ActivityFilter		f )
	{
		gop.setFilter(
			new GeneralOpsPanel.ActivityFilter(){
				
				@Override
				public boolean 
				accept(
					Activity activity)
				{
					DHTControlActivity	target;
					
					synchronized( activity_map ){
						
						target = activity_reverse_map.get( activity );						
					}
					
					if ( target != null ){
						
						return( f.accept( target ));
						
					}else{
						return( false );
					}
				}
			});
	}

		/**
		 * @param min things don't work well for < 4...
		 */

	public void
	setMinimumSlots(
		int		min )
	{
		gop.setMinimumSlots(min);
	}

	public void
	setScaleAndRotation(
		float		min_x,
		float		max_x,
		float		min_y,
		float		max_y,
		double		rot )
	{
		gop.setScaleAndRotation(min_x, max_x, min_y, max_y, rot);
	}

	public void
	refresh()
	{
		gop.refresh();
	}



	public void delete()
	{
		if ( current_dht != null ){

			current_dht.getControl().removeListener( this );

			current_dht = null;
		}

		gop.delete();
	}

	private class
	ActivityHolder
		implements GeneralOpsPanel.Activity
	{
		private final DHTControlActivity		delegate;
		
		private Map<DHTControlActivity.ActivityNode, NodeHolder>	node_map = new IdentityHashMap<>();
		
		private
		ActivityHolder(
			DHTControlActivity	_delegate )
		{
			delegate = _delegate;
		}
				
		public String
		getDescription()
		{
			return( delegate.getDescription());
		}
		
		public int
		getType()
		{
			int	type = delegate.getType();
			
			switch( type ){
				case DHTControlActivity.AT_EXTERNAL_GET:
					return( TYPE_1 );
				case DHTControlActivity.AT_INTERNAL_GET:
					return( TYPE_2 );
				case DHTControlActivity.AT_EXTERNAL_PUT:
					return( TYPE_3 );
				default:
					return( TYPE_DEFAULT );
			}
		}
		
		public boolean
		isQueued()
		{
			return( delegate.isQueued());
		}
		
		public State
		getCurrentState()
		{
			return( new StateHolder( delegate.getCurrentState()));
		}

		private Node
		map(
			DHTControlActivity.ActivityNode node )
		{
			NodeHolder holder;
			
			synchronized( node_map ){
				
				holder = node_map.get( node );
				
				if ( holder == null ){
					
					holder = new NodeHolder( node );
					
					node_map.put( node, holder );
				}
			}
			
			return( holder );
		}
		
		private class
		StateHolder
			implements GeneralOpsPanel.State
		{
			private final DHTControlActivity.ActivityState	delegate;
			
			private 
			StateHolder(
				DHTControlActivity.ActivityState	_delegate )
			{
				delegate = _delegate;
			}
			
			public Node
			getRootNode()
			{
				return( map( delegate.getRootNode()));
			}
			

			
			public int
			getDepth()
			{
				return( delegate.getDepth());
			}
			
			public String
			getResult()
			{
				return( delegate.getResult());
			}
		}
		
		private class
		NodeHolder
			implements GeneralOpsPanel.Node
		{
			private final DHTControlActivity.ActivityNode	delegate;
			
			private
			NodeHolder(
				DHTControlActivity.ActivityNode		_delegate )
			{
				delegate = _delegate;
			}
			
			public List<Node>
			getChildren()
			{
				List<DHTControlActivity.ActivityNode>	kids = delegate.getChildren();
				
				List<Node>	result = new ArrayList<>();
				
				for ( DHTControlActivity.ActivityNode n: kids ){
					
					result.add( map( n ));
				}
				
				return( result );
			}
		}
	}
	
	public interface
	ActivityFilter
	{
		public boolean
		accept(
			DHTControlActivity		activity );
	}
}
