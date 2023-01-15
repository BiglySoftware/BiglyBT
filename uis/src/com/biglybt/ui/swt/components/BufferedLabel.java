/*
 * File    : BufferedLabel.java
 * Created : 24-Nov-2003
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

package com.biglybt.ui.swt.components;

/**
 * @author parg
 *
 */


import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;

public class
BufferedLabel
	extends BufferedWidget
{
	private Control	label;

	private String	value = "";

	public
	BufferedLabel(
		Composite	composite,
		int			attrs )
	{
		super((attrs&SWT.DOUBLE_BUFFERED)==0?new Label( composite, attrs ):new DoubleBufferedLabel( composite, attrs ));

		label = (Control)getWidget();

		ClipboardCopy.addCopyToClipMenu(
			label,
			new ClipboardCopy.copyToClipProvider()
			{
				@Override
				public String
				getText()
				{
					return( BufferedLabel.this.getText());
				}
			});
	}

	public
	BufferedLabel(
		Composite							composite,
		int									attrs,
		ClipboardCopy.copyToClipProvider	clip_provider )
	{
		super((attrs&SWT.DOUBLE_BUFFERED)==0?new Label( composite, attrs ):new DoubleBufferedLabel( composite, attrs ));

		label = (Control)getWidget();

		ClipboardCopy.addCopyToClipMenu( label, clip_provider );
	}
	
	public boolean
	isDisposed()
	{
		return( label.isDisposed());
	}

	public void
	setLayoutData(
		GridData	gd )
	{
		label.setLayoutData( gd );
	}

	public void
	setLayoutData(
		FormData	gd )
	{
		label.setLayoutData( gd );
	}

	public void
	setLayoutData(
		Object	ld )
	{
		label.setLayoutData( ld );
	}

	public void
	setData(
		String	key,
		Object	value )
	{
		label.setData(key,value);
	}

	public Object
	getData(
		String	key )
	{
		return( label.getData( key ));
	}

	public Control
	getControl()
	{
		return( label );
	}

	public void
	setText(
		String	new_value )
	{
		if ( label.isDisposed()){
			return;
		}

		if ( new_value == value ){

			return;
		}

		if (	new_value != null &&
				value != null &&
				new_value.equals( value )){

			return;
		}

		value = new_value;

			// '&' chars that occur in the text are treated as accelerators and, for example,
			// cause the nect character to be underlined on Windows. This is generally NOT
			// the desired behaviour of a label so by default we escape them

		String fixed_value = value==null?"":value.replaceAll("&", "&&" );

		if ( label instanceof Label ){

			((Label)label).setText( fixed_value );
		}else{

			((DoubleBufferedLabel)label).setText( fixed_value );
		}
	}

	public void
	setLink(
		String		url )
	{
		Object[]	existing = (Object[])label.getData();

		if ( existing == null && url == null ){

			return;

		}else if ( existing == null || url == null ){

		}else if (((String[])existing)[0].equals( url )){

			return;
		}


		if ( url == null ){
			label.setData( null );
			label.setCursor( null );
			label.setForeground( null );
			Utils.setTT(label, null );
		}else{
			final String[] data = new String[]{ url };

			label.setData( data );

			Utils.setTT(label,url);

		    label.setCursor(label.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		    Utils.setLinkForeground(label);
		    label.addMouseListener(new MouseAdapter() {
		      @Override
		      public void mouseDoubleClick(MouseEvent arg0) {
		      	showURL((Label)arg0.widget);
		      }
		      @Override
		      public void mouseUp(MouseEvent arg0) {
		    	  showURL((Label)arg0.widget);
		      }

		      protected void
		      showURL(
		    	Label label )
		      {
		    	  if ( label.getData() == data ){

		    		  Utils.launch( data[0] );

		    	  }else{

		    		  label.removeMouseListener( this );
		    	  }
		      }
		    });

		    ClipboardCopy.addCopyToClipMenu( label );
		}
	}

	public void
	setTextAndTooltip(
		String	str )
	{
		setText( str );
		setToolTipText( str );
	}
	
  public String getText() {
    return value==null?"":value;
  }

  public void addMouseListener(MouseListener listener) {
    label.addMouseListener(listener);
  }

  public void setForeground(Color color) {
	  Utils.setSkinnedForeground( label, color);
  }

  public void setBackground(Color color) {
	   label.setBackground(color);
  }
  
  public void setCursor(Cursor cursor) {
    label.setCursor(cursor);
  }

  public void setToolTipText(String toolTipText) {
	  String tt = label.getToolTipText();
	  if ( tt != toolTipText ){
		  if ( tt == null || toolTipText == null || !tt.equals( toolTipText )){
			  Utils.setTT(label,toolTipText);
		  }
	  }
  }

}
