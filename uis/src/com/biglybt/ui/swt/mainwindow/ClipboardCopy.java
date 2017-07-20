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

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Utils;


/**
 * @author Olivier Chalouhi
 *
 */
public class ClipboardCopy {

  public static void
  copyToClipBoard(
    final String    data )
  {
	  Runnable do_it =
		new Runnable()
	  	{
		  @Override
		  public void
		  run()
		  {
			  new Clipboard(Utils.getDisplay()).setContents(
					  new Object[] {data.replaceAll("\\x00", " " )  },
					  new Transfer[] {TextTransfer.getInstance()});
		  }
	  	};

	  if ( Utils.isSWTThread()){

		  do_it.run();

	  }else{

		  Utils.execSWTThread( do_it );
	  }
  }

  public static void
  addCopyToClipMenu(
	final Control				control,
	final copyToClipProvider	provider )
  {
	  control.addMouseListener(
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

				  if ( control.getMenu() != null || text == null || text.length() == 0 ){

					  return;
				  }

				  if (!(e.button == 3 || (e.button == 1 && e.stateMask == SWT.CONTROL))){

					  return;
				  }

				  final Menu menu = new Menu(control.getShell(),SWT.POP_UP);

				  MenuItem   item = new MenuItem( menu,SWT.NONE );

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
		  });
  }

  public static void
  addCopyToClipMenu(
	final Menu		menu,
	final String	text )
  {
	  MenuItem   item = new MenuItem( menu,SWT.NONE );

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
				  return((String)control.getData());
			  }
		});
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
