/*
 * Created on 10 juil. 2003
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

import com.biglybt.ui.swt.config.generic.GenericIntParameter;

/**
 * Creates a config {@link Parameter} linked to a {@link GenericIntParameter}
 *
 * @author Olivier
 *
 */
public class
IntParameter
	extends Parameter
{
  protected GenericIntParameter	delegate;

  public IntParameter(Composite composite, final String name) {
  	super(name);
	  delegate = new GenericIntParameter(config_adapter, composite, name);
  }

  /**
   * @deprecated defaultValue should be set via ConfigurationDefaults, not passed by the caller
   */
  public IntParameter(Composite composite, final String name, int defaultValue) {
  	super(name);
	  delegate = new GenericIntParameter(config_adapter, composite, name,
				defaultValue);
  }


  public IntParameter(Composite composite,
                      final String name,
                      int minValue,
                      int maxValue) {
  	super(name);
	  delegate = new GenericIntParameter(config_adapter, composite, name,
				minValue, maxValue);
  }

  // @see com.biglybt.ui.swt.config.Parameter#isInitialised()
  @Override
  public boolean
  isInitialised()
  {
	  return( delegate != null );
  }

  public void
  setMinimumValue(
  	int		value )
  {
	  delegate.setMinimumValue( value );
  }
  public void
  setMaximumValue(
  	int		value )
  {
  	delegate.setMaximumValue( value );
  }

  public void
  setValue(
  	int		value )
  {
	  delegate.setValue( value );
  }

  public void
  resetToDefault()
  {
	  delegate.resetToDefault();
  }

  public int
  getValue()
  {
  	return( delegate.getValue());
  }

  // @see com.biglybt.ui.swt.config.IParameter#setLayoutData(java.lang.Object)
  @Override
  public void setLayoutData(Object layoutData) {
   delegate.setLayoutData( layoutData );
  }

  // @see com.biglybt.ui.swt.config.IParameter#getControl()
  @Override
  public Control
  getControl()
  {
  	return( delegate.getControl());
  }

	public boolean isGeneratingIntermediateEvents() {
		return delegate.isGeneratingIntermediateEvents();
	}

	public void setGenerateIntermediateEvents(boolean generateIntermediateEvents) {
		delegate.setGenerateIntermediateEvents(generateIntermediateEvents);
	}

  // @see com.biglybt.ui.swt.config.Parameter#setValue(java.lang.Object)
  @Override
  public void setValue(Object value) {
  	if (value instanceof Number) {
  		setValue(((Number)value).intValue());
  	}
  }

  // @see com.biglybt.ui.swt.config.Parameter#getValueObject()
  @Override
  public Object getValueObject() {
  	return new Integer(getValue());
  }
}