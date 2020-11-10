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
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.control.DHTControlActivity;
import com.biglybt.core.dht.control.DHTControlListener;
import com.biglybt.core.util.SystemTime;
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

	public void
	setID( String id ){
		gop.setID( id );
	}
	
	@Override
	public void
	activityChanged(
		DHTControlActivity	activity,
		int					type )
	{
		ActivityHolder	holder;
		
		boolean removed = type == DHTControlListener.CT_REMOVED;
		
		List<ActivityHolder>	expired = null;
		
		synchronized( activity_map ){
			
			if ( removed ){
				
				holder = activity_map.remove( activity );
				
				if ( holder != null ){
				
					activity_reverse_map.remove( holder );
				}
				
			}else{
				
				holder = activity_map.get( activity );
				
				if ( holder == null ){
					
					holder = new ActivityHolder( activity );
					
					activity_map.put( activity, holder );
					
					activity_reverse_map.put( holder, activity );
					
						// seeing an undiagnosed leak in this area where activities build up - put a hard limit
						// on how many we'll keep track of
					
					if ( activity_map.size() > 250 ){
						
						List<ActivityHolder> holders = new ArrayList<>( activity_map.values());
						
						Collections.sort(
							holders,
							new Comparator<ActivityHolder>()
							{
								public int
								compare(
									ActivityHolder	a1,
									ActivityHolder	a2 )
								{
									long res = a1.getCreateTime() - a2.getCreateTime();
									
									if ( res < 0 ){
										return( -1 );
									}else if ( res > 0 ){
										return( 1 );
									}else{
										return( 0 );
									}
								}
							});
						
						expired = holders.subList(0,  Math.min( holders.size(), 50 ));
						
						for ( ActivityHolder h: expired ){
							
							activity_map.remove( h.getActivity());
							
							activity_reverse_map.remove( h );
						}
					}
				}
			}
		}
		
		if ( expired != null ){
		
			for ( ActivityHolder h: expired ){
			
				gop.activityChanged( h, true );
			}
		}
		
		if ( holder != null ){
		
			gop.activityChanged( holder, removed );
		}
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
		private final long						create_time = SystemTime.getMonotonousTime();
		
		private final DHTControlActivity		delegate;
				
		private
		ActivityHolder(
			DHTControlActivity	_delegate )
		{
			delegate = _delegate;
		}
		
		public DHTControlActivity
		getActivity()
		{
			return( delegate );
		}
		
		public long
		getCreateTime()
		{
			return( create_time );
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
			DHTControlActivity.ActivityState state = delegate.getCurrentState();
			
			if ( state == null ){
				
					// can legitimately be null
				
				return( null );
				
			}else{
				
				return( new StateHolder( state ));
			}
		}

		private Node
		map(
			DHTControlActivity.ActivityNode node )
		{		
			return( new NodeHolder( node ));
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
