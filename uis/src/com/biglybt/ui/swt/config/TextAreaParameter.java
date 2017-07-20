/*
 * Created on 9 juil. 2003
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
package com.biglybt.ui.swt.config;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.util.AERunnable;
import com.biglybt.pif.ui.components.UIComponent;
import com.biglybt.pif.ui.components.UIPropertyChangeEvent;
import com.biglybt.pif.ui.components.UIPropertyChangeListener;
import com.biglybt.pifimpl.local.ui.config.UITextAreaImpl;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;


public class
TextAreaParameter
	extends 	Parameter
	implements	UIPropertyChangeListener
{
	private UITextAreaImpl	ui_text_area;

	private StyledText	text_area;

	public
	TextAreaParameter(
		Composite 			composite,
		UITextAreaImpl 		_ui_text_area)
	{
		super( "" );

		ui_text_area = _ui_text_area;

		text_area = new StyledText(composite,SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);

		ClipboardCopy.addCopyToClipMenu(
				text_area,
				new ClipboardCopy.copyToClipProvider()
				{
					@Override
					public String
					getText()
					{
						return( text_area.getText().trim());
					}
				});

		text_area.addKeyListener(
				new KeyAdapter()
				{
					@Override
					public void
					keyPressed(
						KeyEvent event )
					{
						int key = event.character;

						if ( key <= 26 && key > 0 ){

							key += 'a' - 1;
						}

						if ( key == 'a' && event.stateMask == SWT.MOD1 ){

							event.doit = false;

							text_area.selectAll();
						}
					}
				});

		text_area.setText(ui_text_area.getText());

		ui_text_area.addPropertyChangeListener(this);
	}

	@Override
	public void
	setLayoutData(
		Object layoutData )
	{
		if ( layoutData instanceof GridData ){

			GridData gd = (GridData)layoutData;

			Integer hhint = (Integer)ui_text_area.getProperty( UIComponent.PT_HEIGHT_HINT );

			if ( hhint != null ){

				gd.heightHint = hhint;
			}
		}

  	Utils.adjustPXForDPI(layoutData);
		text_area.setLayoutData(layoutData);
	}


	@Override
	public Control
	getControl()
	{
		return( text_area );
	}

	@Override
	public void
	setValue(
		Object value)
	{
	}

	@Override
	public void
	propertyChanged(
		final UIPropertyChangeEvent ev )
	{
		if ( text_area.isDisposed() || !ui_text_area.isVisible()){

			ui_text_area.removePropertyChangeListener( this );

			return;
		}

		text_area.getDisplay().asyncExec(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					if ( text_area.isDisposed() || !ui_text_area.isVisible()){

						ui_text_area.removePropertyChangeListener( TextAreaParameter.this );

						return;
					}

					String old_value = (String)ev.getOldPropertyValue();
					String new_value = (String) ev.getNewPropertyValue();

					ScrollBar bar = text_area.getVerticalBar();

					boolean max = bar.getSelection() == bar.getMaximum() - bar.getThumb();

					int lineOffset = text_area.getLineCount() - text_area.getTopIndex();

					if ( new_value.startsWith( old_value )){

						String toAppend = new_value.substring(old_value.length());

						if ( toAppend.length() == 0 ){

							return;
						}

						StringBuilder builder = new StringBuilder(toAppend.length());

						String[] lines = toAppend.split("\n");


						for( int i=0;i<lines.length;i++){

							String line = lines[i];

							builder.append("\n");
							builder.append(line);
						}

						text_area.append(builder.toString());

					}else{

						StringBuilder builder = new StringBuilder(new_value.length());

						String[] lines = new_value.split("\n");

						for( int i=0;i<lines.length;i++){

							String line = lines[i];

							if (line != lines[0] ){

								builder.append("\n");
							}

							builder.append(line);
						}

						text_area.setText(builder.toString());
					}

					if ( max ){

						bar.setSelection(bar.getMaximum()-bar.getThumb());

						text_area.setTopIndex(text_area.getLineCount()-lineOffset);

						text_area.redraw();
					}

				}
			});
	}
}
