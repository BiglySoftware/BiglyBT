/*
 * Created on 27-Apr-2004
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

package com.biglybt.pifimpl.local.ui.components;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;

import com.biglybt.core.util.*;
import com.biglybt.pif.ui.components.UIPropertyChangeListener;
import com.biglybt.pif.ui.components.UITextArea;


public class
UITextAreaImpl
	extends		UIComponentImpl
	implements 	UITextArea
{
	private final boolean enable_history = System.getProperty( "az.logging.keep.ui.history", "true" ).equals( "true" );

	private int	max_size		= DEFAULT_MAX_SIZE;
	private int max_file_size = 20 * max_size;

	PoopWriter pw;
	int current_file_size;
	File poop_file;
	boolean useFile = true;

	AEMonitor file_mon = new AEMonitor("filemon");

	LinkedList<String>	delay_text	= new LinkedList<>();
	int					delay_size	= 0;

	FrequencyLimitedDispatcher	dispatcher =
		new FrequencyLimitedDispatcher(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					delayAppend();
				}
			},
			500 );

	public
	UITextAreaImpl()
	{
		setText("");
	}

	@Override
	public void
	setText(
		String		text )
	{
		if ( !enable_history ){

			return;
		}

		if ( useFile ){

			try{
				file_mon.enter();

				if ( pw == null ){

					pw = new PoopWriter();

					pw.print(text);

					current_file_size = text.length();

					return;
				}
			}finally{

				file_mon.exit();
			}
		}

		// has property change listener, or error while doing file (fallthrough)

		if ( text.length() > max_size ){

			int	size_to_show = max_size - 10000;

			if ( size_to_show < 0 ){

				size_to_show	= max_size;
			}

			text = text.substring( text.length() - size_to_show );
		}

		setProperty( PT_VALUE, text );
	}

	@Override
	public void
	appendText(
		String		text )
	{
		if ( !enable_history ){

			return;
		}

		if ( useFile && pw != null ){

			try{
				file_mon.enter();

					// shrink the file occasionally

				if ( current_file_size > max_file_size ){

					current_file_size = getFileText().length();
				}

				pw.print(text);

				current_file_size += text.length();

				return;

			}finally{

				file_mon.exit();
			}
		}

		synchronized( this ){

			delay_text.addLast( text );

			delay_size += text.length();

			while( delay_size > max_size ){

				if ( delay_text.size() == 0 ){

					break;
				}

				String	s = (String)delay_text.removeFirst();

				delay_size -= s.length();
			}
		}

		dispatcher.dispatch();
	}

	protected void
	delayAppend()
	{
		String	str = getText();

		String	text;

		synchronized( this ){

			if ( delay_text.size() == 1 ){

				text = (String)delay_text.get(0);

			}else{

				StringBuilder sb = new StringBuilder( delay_size );

				Iterator<String>	it = delay_text.iterator();

				while( it.hasNext()){

					sb.append( it.next());
				}

				text = sb.toString();
			}

			delay_text.clear();
			delay_size = 0;
		}

		if ( str == null ){

			setText( text );

		}else{

			setText( str + text );
		}
	}

	@Override
	public String
	getText()
	{
		if ( !enable_history ){

			return( "" );
		}

		if ( useFile && pw != null ){

			return( getFileText());
		}

		return((String)getProperty( PT_VALUE ));
	}

	@Override
	public void
	setMaximumSize(
		int	_max_size )
	{
		max_size	= _max_size;
	}

	private String
	getFileText()
	{
		try{
			file_mon.enter();

			String text = null;

			if ( pw != null ){

				pw.close();

				text = pw.getText();
			}

			if ( text == null ){

				text = "";
			}

			pw = null;

			if ( useFile ){

				pw = new PoopWriter();

				pw.print(text);

				current_file_size = text.length();
			}

			return text;

		}finally{

			file_mon.exit();
		}
	}

	@Override
	public void
	addPropertyChangeListener(
		UIPropertyChangeListener l )
	{
		if ( useFile ){

			useFile = false;

			setText( getFileText());
		}

		super.addPropertyChangeListener(l);
	}

	protected class
	PoopWriter
	{
		private StringBuffer	buffer = new StringBuffer(256);

		private PrintWriter		pw;

		private void
		print(
			String	text )
		{
			if ( pw == null ){

				buffer.append( text );

				if ( buffer.length() > 8*1024 ){

					if ( poop_file == null ){

						try{
							poop_file = AETemporaryFileHandler.createTempFile();

						}catch( Throwable e ){
						}
					}

					if ( poop_file != null ){

						try{
							pw = new PrintWriter( new OutputStreamWriter( FileUtil.newFileOutputStream( poop_file ), "UTF-8" ));

							pw.print( buffer.toString());

						}catch( Throwable e ){
						}
					}

					buffer.setLength( 0 );
				}
			}else{

				pw.print( text );
			}
		}

		private String
		getText()
		{
			if ( poop_file == null ){

				return( buffer.toString());

			}else{

				try{
					return( FileUtil.readFileEndAsString( poop_file, max_size, "UTF-8" ));

				}catch( Throwable e ){

					return( "" );
				}
			}
		}

		private void
		close()
		{
			if ( pw != null ){

				pw.close();

				pw = null;
			}
		}
	}
}
