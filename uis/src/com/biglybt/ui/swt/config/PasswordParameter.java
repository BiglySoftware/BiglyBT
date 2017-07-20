/*
 * Created on 8 september 2003
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

import java.security.MessageDigest;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.config.*;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SHA1Hasher;
import com.biglybt.ui.swt.Utils;

/**
 * @author Olivier
 *
 */
public class
PasswordParameter
	extends Parameter
{
  String name;
  Text inputField;

  public
  PasswordParameter(
  	Composite composite,
	final String name)
  {
  	this( composite, name, com.biglybt.pif.ui.config.PasswordParameter.ET_SHA1 );
  }

  public
  PasswordParameter(
  	Composite 		composite,
	final String 	name,
	final int		encoding )
  {
  	super(name);
    this.name = name;
    inputField = new Text(composite, SWT.BORDER);
    inputField.setEchoChar('*');
    byte[] value = COConfigurationManager.getByteParameter(name, "".getBytes());
    if(value.length > 0)
      inputField.setText("***");
    inputField.addListener(SWT.Modify, new Listener() {
      @Override
      public void handleEvent(Event event) {
        try{
          String	password_string = inputField.getText();

          byte[] password = password_string.getBytes();
          byte[] encoded;
          if(password.length > 0 ){
        	  if ( encoding == com.biglybt.pif.ui.config.PasswordParameter.ET_PLAIN ){

        		 encoded = password;

        	  }else if ( encoding == com.biglybt.pif.ui.config.PasswordParameter.ET_SHA1 ){

       	         SHA1Hasher hasher = new SHA1Hasher();

       	         encoded = hasher.calculateHash(password);

        	  }else{

        		  	// newly added, might as well go for UTF-8

        		 encoded = MessageDigest.getInstance( "md5").digest( password_string.getBytes( "UTF-8" ));
        	  }
          }else{
            encoded = password;
          }

          COConfigurationManager.setParameter(name, encoded);
        } catch(Exception e) {
        	Debug.printStackTrace( e );
        }
      }
    });
  }

  @Override
  public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
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

    if (!COConfigurationManager.getParameter(name).equals(value)) {
    	COConfigurationManager.setParameter(name, value);
    }
  }

  public String getValue() {
    return inputField.getText();
  }

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
}
