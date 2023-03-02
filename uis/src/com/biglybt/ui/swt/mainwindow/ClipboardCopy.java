/*
 * Created on 3 mai 2004
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
package com.biglybt.ui.swt.mainwindow;

import java.util.LinkedList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnableObject;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.PluginInterface;
import com.biglybt.ui.swt.Utils;


/**
 * @author Olivier Chalouhi
 *
 */
public class ClipboardCopy {

	private static String MENU_ITEM_KEY 		= "ClipboardCopy.mi";
	private static String MOUSE_LISTENER_KEY 	= "ClipboardCopy.ml";
	
	private static LinkedList<Object[]>	history = new LinkedList<>();
	
	public static void
	copyToClipBoard(
		String    data )
	{
		copyToClipBoard( data, null );
	}
	  
	public static void
	copyToClipBoard(
		String    			data,
		PluginInterface		originator )
	{
		synchronized( history ){
			
			history.add( new Object[]{ data, originator });
					
			if ( history.size() > 32 ){
				
				history.removeLast();
			}
		}
			
		Utils.execSWTThread(
				() -> new Clipboard(Utils.getDisplay()).setContents(new Object[] {
						data.replaceAll("\\x00", " ")
				}, new Transfer[] {
						TextTransfer.getInstance()
				}));
	}
	
		/**
		 * 
		 * @return originator of the clipboard text if known
		 */
	
	public static PluginInterface
	getOriginator(
		String		text )
	{
		if ( text != null ){

			synchronized( history ){

				for ( Object[] entry: history ){

					if ( text.equals( (String)entry[0] )){

						return((PluginInterface)entry[1]);
					}
				}
			}
		}

		return( null );
	}
	 
  public static String
  copyFromClipboard()
  {
	  if ( Utils.isSWTThread()){
		  Clipboard clipboard = new Clipboard(Display.getDefault());

		  String text = (String)clipboard.getContents(TextTransfer.getInstance());

		  clipboard.dispose();

		  return( text );
		  
	  }else{
		  
		  return((String)Utils.execSWTThreadWithObject( 
			  "copyFromClipboard",
			  new AERunnableObject(){

				  @Override
				  public Object runSupport(){

					  Clipboard clipboard = new Clipboard(Display.getDefault());

					  String text = (String)clipboard.getContents(TextTransfer.getInstance());

					  clipboard.dispose();

					  return( text );
				  }
			  }, 10*1000 ));
	  }
  }
  
  public static void
  addCopyToClipMenu(
	final Control				control,
	final copyToClipProvider	provider )
  {
	  MouseAdapter ml = (MouseAdapter)control.getData( MOUSE_LISTENER_KEY );
	  
	  if ( ml != null ){
	  
		  control.removeMouseListener( ml );
	  }
	  
	  ml =
		  new MouseAdapter()
		  {
			  @Override
			  public void
			  mouseDown(
				 MouseEvent e )
			  {
				  if ( control.isDisposed()){

					  return;
				  }

				  final String	text = provider.getText();

				  if ( text == null || text.length() == 0 ){
					  
					  return;
				  }
				  
				  Menu temp = control.getMenu();

				  if ( temp != null ){
					  
					  MenuItem[] items = temp.getItems();
					  
					  if ( items.length == 0 ){
						  
						  temp.dispose();
						  
					  }else{
						  
						  return;
					  }
				  }

				  if (!(e.button == 3 || (e.button == 1 && e.stateMask == SWT.CONTROL))){

					  return;
				  }

				  final Menu menu = new Menu(control.getShell(),SWT.POP_UP);

				  MenuItem   item = new MenuItem( menu,SWT.NONE );

				  item.setData( MENU_ITEM_KEY, "" );

				  String	msg_text_id;

				  if ( provider instanceof copyToClipProvider2 ){

					  msg_text_id = ((copyToClipProvider2)provider).getMenuResource();

				  }else{

					  msg_text_id = "label.copy.to.clipboard";
				  }

				  item.setText( MessageText.getString( msg_text_id ));

				  item.addSelectionListener(
						  new SelectionAdapter()
						  {
							  @Override
							  public void
							  widgetSelected(
									  SelectionEvent arg0)
							  {
								  new Clipboard(control.getDisplay()).setContents(new Object[] {text}, new Transfer[] {TextTransfer.getInstance()});
							  }
						  });

				  control.setMenu( menu );

				  menu.addMenuListener(
						  new MenuAdapter()
						  {
							  @Override
							  public void
							  menuHidden(
									  MenuEvent arg0 )
							  {
								  if ( control.getMenu() == menu ){

									  control.setMenu( null );
								  }
							  }
						  });

				  menu.setVisible( true );
			  }
		  };
	  
	  control.setData( MOUSE_LISTENER_KEY, ml );
	  
	  control.addMouseListener( ml );
  }

  public static void
  addCopyToClipMenu(
	final Menu		menu,
	final String	text )
  {
	  for ( MenuItem e: menu.getItems()){
		  
		  if ( e.getData( MENU_ITEM_KEY ) != null ){
			  
			  e.dispose();
		  }
	  }
	  
	  MenuItem   item = new MenuItem( menu,SWT.NONE );

	  item.setData( MENU_ITEM_KEY, "" );

	  String	msg_text_id= "label.copy.to.clipboard";

	  item.setText( MessageText.getString( msg_text_id ));

	  item.addSelectionListener(
		  new SelectionAdapter()
		  {
			  @Override
			  public void
			  widgetSelected(
					  SelectionEvent arg0)
			  {
				  new Clipboard(menu.getDisplay()).setContents(new Object[] {text}, new Transfer[] {TextTransfer.getInstance()});
			  }
		  });
  }

  public static void
  addCopyToClipMenu(
	final Menu					menu,
	final copyToClipProvider	provider )
  {
	  addCopyToClipMenu( menu, "label.copy.to.clipboard", provider );
  }

  public static void
  addCopyToClipMenu(
	Menu				menu,
	String				resource_key, 
	copyToClipProvider	provider )
  {
	  for ( MenuItem e: menu.getItems()){
		  
		  if ( e.getData( MENU_ITEM_KEY ) != null ){
			  
			  e.dispose();
		  }
	  }
	  
	  MenuItem   item = new MenuItem( menu,SWT.NONE );

	  item.setData( MENU_ITEM_KEY, "" );
	  
	  item.setText( MessageText.getString( resource_key ));

	  item.addSelectionListener(
		  new SelectionAdapter()
		  {
			  @Override
			  public void
			  widgetSelected(
					  SelectionEvent arg0)
			  {
				  new Clipboard(menu.getDisplay()).setContents(new Object[] { provider.getText()}, new Transfer[] {TextTransfer.getInstance()});
			  }
		  });
  }
  
  public static void
  removeCopyToClipMenu(
	final Menu					menu )
  {
	  for ( MenuItem e: menu.getItems()){
		  
		  if ( e.getData( MENU_ITEM_KEY ) != null ){
			  
			  e.dispose();
		  }
	  }	
  }
	
  public static void
  addCopyToClipMenu(
	final Control				control )
  {
	  addCopyToClipMenu(
		control,
		new copyToClipProvider()
		{
			  @Override
			  public String
			  getText()
			  {
				  Object o = control.getData();
	  
				  if ( o instanceof String ){
					  
					  return((String)o);
					  
				  }else if ( o instanceof String[]){
					  
					  String[] strs = (String[])o;
					  
					  String str = "";
					  
					  for ( String s: strs ){
						  
						  str += (str.isEmpty()?"":", ") + s;
					  }
					  
					  return( str );

				  }else if (control instanceof Label) {
					  return ((Label) control).getText();
				  }else if (control instanceof Link) {
					  String text =  ((Link) control).getText();
					  
					  String url = UrlUtils.parseTextForURL( text,  false );
					  
					  if ( url != null && !url.isEmpty()){
						  
						  return( url );
					  }
					  
					  return( text );
				  }else{
					  
					  return( String.valueOf( o ));
				  }
			  }
		});
  }
  
  public static void
  removeCopyToClipMenu(
		Control		control )
  {
	  MouseAdapter ml = (MouseAdapter)control.getData( MOUSE_LISTENER_KEY );
	  
	  if ( ml != null ){
	  
		  control.removeMouseListener( ml );
	  }
  }

  public interface
  copyToClipProvider
  {
	  public String
	  getText();
  }

  public interface
  copyToClipProvider2
  	extends copyToClipProvider
  {
	  public String
	  getMenuResource();
  }
}
