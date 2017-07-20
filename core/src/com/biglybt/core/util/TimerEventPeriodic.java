/*
 * File    : TimerEventPeriodic.java
 * Created : 07-Dec-2003
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

package com.biglybt.core.util;

/**
 * @author parg
 *
 */
public class
TimerEventPeriodic
	implements TimerEventPerformer
{
	private final Timer					timer;
	private final long					frequency;
	private final boolean					absolute;
	private final TimerEventPerformer		performer;

	private String				name;
	private TimerEvent			current_event;
	private boolean				cancelled;

	protected
	TimerEventPeriodic(
		Timer				_timer,
		long				_frequency,
		boolean				_absolute,
		TimerEventPerformer	_performer )
	{
		timer		= _timer;
		frequency	= _frequency;
		absolute	= _absolute;
		performer	= _performer;

		long	 now = SystemTime.getCurrentTime();

		current_event = timer.addEvent(	now, now + frequency, absolute, this );
	}

	public void
	setName(
		String	_name )
	{
		name	= _name;

		synchronized( this ){

			if ( current_event != null ){

				current_event.setName( name );
			}
		}
	}

	public String
	getName()
	{
		return( name );
	}

	protected TimerEventPerformer
	getPerformer()
	{
		return( performer );
	}

	public long
	getFrequency()
	{
		return( frequency );
	}

	public boolean
	isCancelled()
	{
		return( cancelled );
	}

	@Override
	public void
	perform(
		TimerEvent	event )
	{
		if ( !cancelled ){

			try{
				performer.perform( event );

			}catch( Throwable e ){

				DebugLight.printStackTrace( e );
			}

			synchronized( this ){

				if ( !cancelled ){

					long	 now = SystemTime.getCurrentTime();

					current_event = timer.addEvent(name, now, now + frequency, absolute, this );
				}
			}
		}
	}

	public synchronized void
	cancel()
	{
		if ( current_event != null ){

			current_event.cancel();

			cancelled	= true;
		}
	}

	protected String
	getString()
	{
		TimerEvent ce = current_event;

		String	ev_data;

		if ( ce == null ){

			ev_data = "?";
		}else{

			ev_data = "when=" + ce.getWhen() + ",run=" + ce.hasRun() + ", can=" + ce.isCancelled();
		}

		return( ev_data  + ",freq=" + getFrequency() + ",target=" + getPerformer() + (name==null?"":(",name=" + name )));
	}
}
