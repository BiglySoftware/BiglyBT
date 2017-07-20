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

package com.biglybt.ui.console;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Vector;

/**
 *
 * @author  tobi
 */
public class CommandReader extends Reader {

  private final static int ENTER = 0;
  private final static int TAB = 1;
  private final static int QUOTE = 3;
  private final static int ESCAPE = 4;
  private final static int NONQUOTEDESCAPE = 5;

  private Reader in;

  /** Creates a new instance of CommandReader */
  public CommandReader(Reader _in) {
    super();
    in = _in;
  }

  private void ensureOpen() throws java.io.IOException {
    if (in == null)
      throw new IOException("Stream closed");
  }

  @Override
  public void close() throws java.io.IOException {
    synchronized(lock) {
      if (in != null) {
        in.close();
        in = null;
      }
    }
  }

  @Override
  public int read() throws java.io.IOException {
    synchronized(lock) {
      ensureOpen();
      return in.read();
    }
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws java.io.IOException {
    synchronized(lock) {
      ensureOpen();
      return in.read(cbuf, off, len);
    }
  }

  public String readLine() throws java.io.IOException {
  	synchronized(lock) {
  		ensureOpen();
  		StringBuilder line = new StringBuilder();
  		int ch;
  		while( (char)(ch = in.read()) != '\n' )
  		{
  			if( ch == -1 )
  			{
  				throw new IOException("stream closed");
  			}
  			line.append((char)ch);
  		}
  		return line.toString().trim();
  	}
  }
  public List parseCommandLine( String commandLine )
  {
  	StringBuffer current = new StringBuffer();
  	Vector args = new Vector();
  	boolean allowEmpty = false;
  	boolean bailout = commandLine.length() == 0;
  	int index = 0;
    int state = ENTER;

  	while (!bailout) {

  		int ch = commandLine.charAt(index++);
  		bailout = (index == commandLine.length());
  		char c = (char) ch;

//  		if (c!='\n'){
//
//  			line.append( c );
//  		}
//
  		switch (state) {
  		/*case SKIP:
  		 switch (c) {
  		 case ' ': case '\t':
  		 break;
  		 case '\"':
  		 mode = QUOTE;
  		 break;
  		 case '&':
  		 background = true;
  		 case ';':
  		 contLine = line.substring(pos +1);
  		 pos = line.length();
  		 break;
  		 default:
  		 mode = READ;
  		 --pos;
  		 }
  		 break;*/

  		case ENTER:
  			switch (c) {
  			case '\"':
  				state = QUOTE;
  				break;
  				/*case ' ': case '\t':
  				 mode = SKIP;
  				 break;*/
  			case  '\\':
  				state = NONQUOTEDESCAPE;
  				break;
//  			case '\n':
//  				bailout = true;
//  				break;
  			case '\r':
  				break;
  			default:
  				current.append(c);
  			}
  			if ((state == ENTER) && ((c==' ') || (bailout))) {
  				String arg = current.toString().trim();
  				if( arg.length() > 0 || allowEmpty )
  				{
  					args.addElement(arg);
  					allowEmpty = false;
  				}
  				current = new StringBuffer();
  			}
  			break;

  		case QUOTE:
  			switch (c) {
  			case '\"':
  				allowEmpty = true;
  				state = ENTER;
  				break;
  			case '\\':
  				state = ESCAPE;
  				break;
  			default:
  				current.append(c);
  			}
  			break;

  		case ESCAPE:
  			switch (c) {
  			case 'n':  c = '\n';  break;
  			case 'r':  c = '\r';  break;
  			case 't':  c = '\t';  break;
  			case 'b':  c = '\b';  break;
  			case 'f':  c = '\f';  break;
  			default: current.append('\\'); break;
  			}
  			state = QUOTE;
  			current.append(c);
  			break;
  		case  NONQUOTEDESCAPE:
  			switch (c) {
  			case  ';':
  				state = ENTER;
  				current.append(c);
  				break;
  			default: // This is not a escaped char.
  				state = ENTER;
  			current.append('\\');
  			current.append(c);
  			break;
  			}
  			break;
  		}
    }
	if ((state == ENTER) && (current.toString().trim().length() > 0 || allowEmpty) )
	{
		String arg = current.toString().trim();
		args.addElement(arg);
	}
  	return args;
  }
}
