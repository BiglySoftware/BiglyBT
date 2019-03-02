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
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;

/**
 * @author Olivier
 *
 */
public class StringAreaParameter extends Parameter{

  private Text inputField;

  public StringAreaParameter(Composite composite, String configID) {
  	super(configID);
    inputField = new Text(composite, SWT.BORDER | SWT.WRAP | SWT.MULTI | SWT.V_SCROLL ) {
  		// I know what I'm doing. Maybe ;)
  		@Override
		  public void checkSubclass() {
  		}

    	// @see org.eclipse.swt.widgets.Text#computeSize(int, int, boolean)
    	@Override
	    public Point computeSize(int wHint, int hHint, boolean changed) {
    		// Text widget, at least on Windows, forces the preferred width
    		// to the width of the text inside of it
    		// Fix this by forcing to LayoutData's minWidth
    		if ( hHint==0 && !isVisible()){

    			return( new Point( 0, 0 ));
    		}
    		Point pt = super.computeSize(wHint, hHint, changed);

    		if (wHint == SWT.DEFAULT) {
      		Object ld = getLayoutData();
      		if (ld instanceof GridData) {
      			if (((GridData)ld).grabExcessHorizontalSpace) {
      				pt.x = 10;
      			}
      		}
    		}


    		return pt;
    	}
    };

    String value = COConfigurationManager.getStringParameter(configID);
    inputField.setText(value);
    inputField.addListener(SWT.Verify, new Listener() {
        @Override
        public void handleEvent(Event e) {
          e.doit = COConfigurationManager.verifyParameter(configID, e.text );
        }
    });

    inputField.addListener(SWT.FocusOut, new Listener() {
        @Override
        public void handleEvent(Event event) {
        	checkValue();
        }
    });

    inputField.addKeyListener(
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

						inputField.selectAll();
					}
				}
			});
  }

  public int
  getPreferredHeight(
	int	line_count )
  {
	  return( inputField.getLineHeight() * line_count );
  }

  protected void
  checkValue()
  {
	  String	old_value = COConfigurationManager.getStringParameter( configID );
	  String	new_value = inputField.getText();

	  if ( !old_value.equals( new_value )){
	      COConfigurationManager.setParameter(configID, new_value );

	      if( change_listeners != null ) {
	        for (int i=0;i<change_listeners.size();i++){
	          ((ParameterChangeListener)change_listeners.get(i)).parameterChanged(StringAreaParameter.this,false);
	        }
	      }
	  }
  }

	@Override
	public void setLayoutData(Object layoutData) {
		inputField.setLayoutData(layoutData);
	}

  public void setValue(final String value) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (inputField == null || inputField.isDisposed()
						|| inputField.getText().equals(value)) {
					return;
				}
				inputField.setText(value);
			}
		});

		if (!COConfigurationManager.getStringParameter(configID).equals(value)) {
			COConfigurationManager.setParameter(configID, value);
		}
	}

  public String getValue() {
    return inputField.getText();
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.IParameter#getControl()
   */
  @Override
  public Control getControl() {
    return inputField;
  }

  @Override
  public void setValue(Object value) {
  	if (value instanceof String) {
  		setValue((String)value);
  	}
  }

  @Override
  public Object getValueObject() {
  	return COConfigurationManager.getStringParameter(configID);
  }
}
