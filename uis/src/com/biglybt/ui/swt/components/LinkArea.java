/*
 * Created on Jul 2, 2007
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


package com.biglybt.ui.swt.components;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import com.biglybt.core.html.HTMLUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;

public class
LinkArea
{
	private StyledText	styled_text;

	  // list of linkInfo for tracking where the links are
	  // could have just used stDecription.getStyleRanges() since we underline
	  // the links, but I didn't want to risk a chance of any other styles
	  // being in there that I don't know about (plus managing the URL)

	private ArrayList links = new ArrayList();

	private int	ofs;

	private String	relative_url_base = "";

	public
	LinkArea(
		Composite	comp )
	{
		styled_text = new StyledText(comp,SWT.BORDER | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL);
		styled_text.setWordWrap(true);

		styled_text.addListener(SWT.MouseUp, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (links.size() == 0) {
					return;
				}
				try {
					int ofs = styled_text.getOffsetAtLocation(new Point(event.x, event.y));
					for (int i = 0; i < links.size(); i++) {
						linkInfo linkInfo = (linkInfo)links.get(i);
						if (ofs >= linkInfo.ofsStart && ofs <= linkInfo.ofsEnd) {
							Utils.launch(linkInfo.url);
							break;
						}
					}
				} catch (Exception e) {

				}
			}
		});

		final Cursor handCursor = new Cursor(comp.getDisplay(), SWT.CURSOR_HAND);
		styled_text.addListener(SWT.MouseMove, new Listener() {
			Cursor curCursor = null;

			@Override
			public void handleEvent(Event event) {
				if (links.size() == 0) {
					return;
				}
				boolean onLink = false;
				try {
					int ofs = styled_text.getOffsetAtLocation(new Point(event.x, event.y));
					for (int i = 0; i < links.size(); i++) {
						linkInfo linkInfo = (linkInfo)links.get(i);
						if (ofs >= linkInfo.ofsStart && ofs <= linkInfo.ofsEnd) {
							onLink = true;
							break;
						}
					}
				} catch (Exception e) {

				}

				try {
					Cursor cursor = onLink ? handCursor : null;
					if (curCursor != cursor) {
						styled_text.setCursor(cursor);
						curCursor = cursor;
					}
				} catch (Exception e) {

				}
			}
		});

		styled_text.addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {
				styled_text.setCursor(null);
				handCursor.dispose();
			}
		});
	}

	public Composite
	getComponent()
	{
		return( styled_text );
	}

	public void
	reset()
	{
		if( styled_text.isDisposed()){
			return;
		}

		ofs = 0;
		styled_text.setText("");

		links.clear();
	}

	public void
	setRelativeURLBase(
		String	str )
	{
		relative_url_base = str;
	}

	public void
	addLine(
		String	line )
	{
		if( styled_text.isDisposed()){
			return;
		}

		try{
			line = HTMLUtils.expand( line );

			Object[]	url_details = HTMLUtils.getLinks( line );

			String	modified_line = (String)url_details[0];

			styled_text.append(modified_line + "\n");

			List	urls = (List)url_details[1];

			for (int i=0;i<urls.size();i++){
				Object[]	entry = (Object[])urls.get(i);

				String	url = (String)entry[0];

				int[]	det = (int[])entry[1];

				if ( !url.toLowerCase().startsWith("http") && relative_url_base.length() > 0 ){

					url = relative_url_base + url;
				}

				linkInfo info = new linkInfo(ofs + det[0], ofs + det[0] + det[1], url );

				links.add(info);

				StyleRange sr = new StyleRange();
				sr.start = info.ofsStart;
				sr.length = info.ofsEnd - info.ofsStart;
				sr.underline = true;
				sr.foreground = styled_text.getDisplay().getSystemColor(SWT.COLOR_LINK_FOREGROUND);

				styled_text.setStyleRange(sr);
			}

			ofs += modified_line.length() + 1;

		}catch( Throwable e ){

				// just in case something borks

			Debug.printStackTrace( e );

			styled_text.append(line + "\n");
		}
	}

	public static class linkInfo {
		int ofsStart;
		int ofsEnd;
		String url;

		linkInfo(int s, int e, String url) {
			ofsStart = s;
			ofsEnd = e;
			this.url = url;
		}
	}
}
