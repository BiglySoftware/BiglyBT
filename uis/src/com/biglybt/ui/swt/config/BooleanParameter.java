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

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.biglybt.ui.swt.config.generic.GenericBooleanParameter;

/**
 * @author Olivier
 *
 */
public class BooleanParameter extends Parameter{
  protected GenericBooleanParameter	delegate;

  public BooleanParameter(Composite composite, final String configID, String textKey) {
  	super(configID);
	  delegate = new GenericBooleanParameter( config_adapter, composite, configID,
         textKey, null);
  }

  @Override
  public boolean
  isInitialised()
  {
	  return( delegate != null );
  }

  @Override
  public void setLayoutData(Object layoutData) {
   delegate.setLayoutData( layoutData );
  }

  public void setAdditionalActionPerformer(IAdditionalActionPerformer actionPerformer) {
	 delegate.setAdditionalActionPerformer( actionPerformer );
  }

  @Override
  public Control getControl() {
    return delegate.getControl();
  }

  public Boolean
  isSelected()
  {
  	return(delegate.isSelected());
  }

  public void
  setSelected(
  	boolean	selected )
  {
  	delegate.setSelected( selected );
  }

  @Override
  public void setValue(Object value) {
  	if (value instanceof Boolean) {
  		setSelected(((Boolean)value).booleanValue());
  	}
  }

  @Override
  public Object getValueObject() {
  	return isSelected();
  }
}
