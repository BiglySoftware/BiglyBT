/*
 * Created on Aug 31, 2016
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


package com.biglybt.ui.swt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.mainwindow.Colors;

public class
TextWithHistory
{
	private static final int MAX_MATCHES	= 10;
	private static final int MAX_HISTORY	= 64;

	private final boolean	disabled;
	private final String	config_prefix;
	private final Text		text;

	private List<String>	history;

	private Shell 	current_shell;
	private org.eclipse.swt.widgets.List	list;

	private boolean	mouse_entered;
	private boolean	menu_visible;

	private boolean	keyDownShowsHistory	= true;
	
	public
	TextWithHistory(
		String	_config_prefix,
		Text	_text )
	{
		config_prefix	= _config_prefix;
		text			= _text;

			// issues around the new shell grabbing focus from the Text field that I can't be bothered
			// to see if I can fix (focus-lost causes shell to be destroyed, can't use TraverseListener
			// to prevent the focus loss - obviously focus loss causes subsequent keystrokes to get
			// lost. Also the List doesn't render selection - bah

		disabled = Constants.isLinux;

		if ( disabled ){

			return;
		}

		loadHistory();

		text.addModifyListener(
			new ModifyListener(){

				@Override
				public void modifyText(ModifyEvent e) {

					if ( !COConfigurationManager.getBooleanParameter( config_prefix + ".enabled", true )){

						if ( current_shell != null ){

							current_shell.dispose();
						}

						return;
					}

					String current_text = text.getText().trim();

					handleSearch( current_text, false );
				}
			});

		text.addFocusListener(
			new FocusListener() {

				@Override
				public void focusLost(FocusEvent e){

					final Shell shell = current_shell;

					if ( shell != null ){

						Utils.execSWTThreadLater(
							mouse_entered?500:0,	// hang around for potential selection event as focus-lost fired from text
													// before selection event fired for combo
							new Runnable()
							{
								@Override
								public void
								run()
								{
									if ( current_shell == shell && !menu_visible ){

										shell.dispose();
									}
								}
							});
					}
				}

				@Override
				public void focusGained(FocusEvent e) {
				}
			});

		text.addKeyListener(new KeyAdapter(){
			@Override
			public void keyPressed(KeyEvent e) {
				int key = e.keyCode;

				if ( list == null || list.isDisposed()){

						// down arrow with no current search shows history

					if ( key == SWT.ARROW_DOWN && keyDownShowsHistory ){
						String current_text = text.getText().trim();
						if ( current_text.length() == 0 ){
							handleSearch(current_text, true);
							e.doit = false;
						}
					}
					return;
				}

				if ( key == SWT.ARROW_DOWN ){
					e.doit = false;
					int curr = list.getSelectionIndex();
					curr++;
					if ( curr < list.getItemCount()){
						list.setSelection( curr );
					}
				}else if ( key == SWT.ARROW_UP ){
					int curr = list.getSelectionIndex();
					curr--;
					if ( curr < 0 ){
						list.deselectAll();
					}else{
						list.setSelection( curr );
					}
					e.doit = false;
				}else if ( key == SWT.CR || key == SWT.LF || key == SWT.KEYPAD_CR){
					if (fireSelected()){
						e.doit = false;
					}
				}
			}
		});
		
			// double-click with no current search shows history
		
		text.addListener(SWT.MouseDoubleClick,(e)->{
			String current_text = text.getText().trim();
			if ( current_text.length() == 0 ){
				handleSearch(current_text, true);
				e.doit = false;
			}
		});
	}

	public void
	setKeDownShowsHistory(
		boolean	b )
	{
		keyDownShowsHistory = b;
	}
	
	private void
	handleSearch(
		String		current_text,
		boolean		force )
	{
		List<String>	current_matches = match( current_text );

		if ( current_text.length() == 0 || current_matches.size() == 0 ){

			if ( !force ){

				if ( current_shell != null ){

					current_shell.dispose();
				}

				return;
			}
		}


		if ( current_shell == null ){

			mouse_entered 	= false;
			menu_visible	= false;

			current_shell = new Shell( text.getShell(), SWT.NO_FOCUS | SWT.NO_TRIM );

			current_shell.addDisposeListener(
				new DisposeListener() {

					@Override
					public void widgetDisposed(DisposeEvent e) {
						current_shell	= null;
						list			= null;
						mouse_entered	= false;
						menu_visible	= false;
					}
				});


		}else{

			String[] items = list.getItems();

			if ( items.length == current_matches.size()){

				boolean	same = true;

				for ( int i=0;i<items.length;i++){
					if ( !items[i].equals( current_matches.get(i))){
						same = false;
						break;
					}
				}

				if ( same ){

					return;
				}
			}

			Utils.disposeComposite( current_shell, false );
		}

		GridLayout layout = new GridLayout();

		layout.marginHeight = 0;
		layout.marginWidth = 0;

		current_shell.setLayout( layout );

		Color	background = text.getBackground();

		current_shell.setBackground( background );

		final Composite comp = new Composite( current_shell, SWT.NULL );
		comp.setLayoutData( new GridData( GridData.FILL_BOTH ));

		layout = new GridLayout();

		layout.marginHeight = 0;
		layout.marginWidth 	= 0;
		layout.marginLeft 	= 2;
		layout.marginRight 	= 2;
		layout.marginBottom = 2;

		comp.setLayout( layout );

		comp.setBackground( background );

		comp.addPaintListener( new PaintListener() {

			@Override
			public void paintControl(PaintEvent e) {
				GC gc = e.gc;

				gc.setForeground( Colors.dark_grey );

				Rectangle bounds = comp.getBounds();

				gc.drawLine( 0, 0, 0, bounds.height-1 );
				gc.drawLine( bounds.width-1, 0, bounds.width-1, bounds.height-1 );
				gc.drawLine( 0, bounds.height-1, bounds.width-1, bounds.height-1 );
			}
		});

		list = new org.eclipse.swt.widgets.List( comp, SWT.NULL );
		list.setLayoutData( new GridData( GridData.FILL_BOTH ));

		for ( String match: current_matches ){

			list.add( match);
		}

		list.setFont( text.getFont());
		list.setBackground( background );

		list.deselectAll();

		list.addMouseMoveListener( new MouseMoveListener() {
			@Override
			public void mouseMove(MouseEvent e){

				int item_height = list.getItemHeight();

				int y = e.y;

				int	item_index = y/item_height;

				if ( list.getSelectionIndex() != item_index ){
					list.setSelection( item_index );
				}
			}
		});

		list.addMouseTrackListener( new MouseTrackAdapter() {
			@Override
			public void mouseEnter(MouseEvent e) {
				mouse_entered = true;
			}
			@Override
			public void mouseExit(MouseEvent e) {
				list.deselectAll();
				mouse_entered = false;
			}
		});

		list.addSelectionListener(
			new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					fireSelected();
				}
			});

		Menu menu = new Menu( list );

		list.setMenu( menu );

			// clear history

		MenuItem mi = new MenuItem( menu, SWT.PUSH );

		mi.setText( MessageText.getString( "label.clear.history" ));

		mi.addSelectionListener(
			new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					clearHistory();
				}
			});

		mi = new MenuItem( menu, SWT.SEPARATOR );

			// disable history

		mi = new MenuItem( menu, SWT.PUSH );

		mi.setText( MessageText.getString( "label.disable.history" ));

		mi.addSelectionListener(
			new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {

					COConfigurationManager.setParameter( config_prefix + ".enabled", false );
				}
			});

		menu.addMenuListener(
			new MenuListener() {

				@Override
				public void menuShown(MenuEvent e) {
					menu_visible = true;
				}

				@Override
				public void menuHidden(MenuEvent e) {
					menu_visible = false;
				}
			});

		current_shell.pack( true );
		current_shell.layout( true, true );

		Rectangle bounds = text.getBounds();

		Point shell_pos = text.toDisplay( 0, bounds.height + (Constants.isOSX?2:0 ));

		current_shell.setLocation( shell_pos );

		Rectangle shell_size = current_shell.getBounds();

		shell_size.width += 4;

		if ( shell_size.width > bounds.width ){

			shell_size.width = bounds.width;

		}else if ( shell_size.width < 200 && bounds.width >= 200 ){

			shell_size.width = 200;
		}

		current_shell.setBounds( shell_size );

		current_shell.setVisible( true );
	}

	private boolean
	fireSelected()
	{
		String[] selection =  list.getSelection();

		if ( selection.length > 0 ){

			String chars = selection[0];

			text.setText( chars );

			text.setSelection( chars.length());

			if ( current_shell != null ){

				current_shell.dispose();
			}

			return( true );

		}else{

			if ( current_shell != null ){

				current_shell.dispose();
			}

			return( false );
		}
	}

	private List<String>
	match(
		String	str )
	{
		str = str.trim();

		List<String>	matches = new ArrayList<>();

		for ( String h: history ){

			h = h.trim();

			if ( h.startsWith( str )){

				matches.add( h );

				if ( matches.size() == MAX_MATCHES ){

					break;
				}
			}
		}

		return( matches );
	}

	private void
	loadHistory()
	{
		final ParameterListener listener = new ParameterListener() {

			@Override
			public void parameterChanged(String name) {
				List<String> sl = COConfigurationManager.getStringListParameter(name);

				history = new ArrayList<>();

				if (sl != null) {

					history.addAll(sl);
				}
			}
		};
		COConfigurationManager.addAndFireParameterListener(
				config_prefix + ".data",
				listener);
		text.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				COConfigurationManager.removeParameterListener(config_prefix + ".data",
						listener);
			}
		});
	}

	private void
	clearHistory()
	{
		String key = config_prefix + ".data";

		List<String> sl = COConfigurationManager.getStringListParameter( key );

		sl.clear();

		COConfigurationManager.setParameter( key, sl );
	}

	public void
	addHistory(
		String		str )
	{
		if ( disabled ){

			return;
		}

		str = str.trim();

		String key = config_prefix + ".data";

		List<String> sl = COConfigurationManager.getStringListParameter( key );

		sl.clear();

		sl.add( str );

		for ( String h: history ){

			if ( !str.startsWith( h )){

				sl.add( h );

				if ( sl.size() == MAX_HISTORY ){

					break;
				}
			}
		}

		COConfigurationManager.setParameter( key, sl );

		COConfigurationManager.setDirty();
	}
}
