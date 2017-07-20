/*
 * Created on 22 Aug 2008
 * Created by Allan Crooks
 * Copyright (C) 2008 Vuze Inc., All Rights Reserved.
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
 */

package com.biglybt.ui.console.util;

import java.io.PrintStream;
import java.util.Iterator;

public class TextWrap {

	public static void printList(Iterator text_segments, PrintStream out, String space_between_commands) {
		StringBuffer command_line_so_far = new StringBuffer("  ");
		while (text_segments.hasNext()) {
			String next_command = (String)text_segments.next();
			int current_length = command_line_so_far.length();
			if (current_length + next_command.length() + space_between_commands.length() > 79) {
				out.println(command_line_so_far);
				command_line_so_far.setLength(2);
			}
			command_line_so_far.append(next_command);
			command_line_so_far.append(space_between_commands);
		}
		if (command_line_so_far.length() > 2) {
			out.println(command_line_so_far);
		}
	}

}
