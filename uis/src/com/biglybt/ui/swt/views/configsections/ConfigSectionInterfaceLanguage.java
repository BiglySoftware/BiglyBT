/*
 * Created on Oct 21, 2004
 * Created by Alon Rohter
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

package com.biglybt.ui.swt.views.configsections;

import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

import com.biglybt.pif.ui.config.ConfigSection;

/**
 *
 */
public class ConfigSectionInterfaceLanguage implements UISWTConfigSection {

  @Override
  public String configSectionGetParentSection() {
    return ConfigSection.SECTION_INTERFACE;
  }

  @Override
  public String configSectionGetName() {
    return "language";
  }

  @Override
  public void configSectionSave() {
  }

  @Override
  public void configSectionDelete() {
  }

	@Override
	public int maxUserMode() {
		return 0;
	}

  @Override
  public Composite configSectionCreate(final Composite parent) {
    Label label;
    GridLayout layout;
    GridData gridData;
    Composite cMain = new Composite( parent,  SWT.NULL );
    cMain.setLayoutData( new GridData( GridData.FILL_BOTH ) );
    layout = new GridLayout();
    layout.numColumns = 1;
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    cMain.setLayout( layout );

    label = new Label( cMain, SWT.NULL );
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    label.setLayoutData(gridData);
    Messages.setLanguageText( label, "MainWindow.menu.language" );  //old name path, but already translated

    Locale[] locales = MessageText.getLocales(true);

    String[] drop_labels = new String[ locales.length ];
    String[] drop_values = new String[ locales.length ];
    int iUsingLocale = -1;
    for( int i=0; i < locales.length; i++ ) {
      Locale locale = locales[ i ];
      String sName = locale.getDisplayName(locale);
      String sName2 = locale.getDisplayName();
      if (!sName.equals(sName2)) {
      	sName += " - " + sName2;
      }
      drop_labels[ i ] = sName + " - " + locale;
      drop_values[ i ] = locale.toString();
      if (MessageText.isCurrentLocale(locale))
      	iUsingLocale = i;
    }

    StringListParameter locale_param = new StringListParameter(cMain, "locale",
				drop_labels, drop_values, false);
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.minimumHeight = 50;
    locale_param.setLayoutData(gridData);
    // There may be no "locale" setting stored in config, so set it to
    // what we are using now.  Don't automatically write it to config, because
    // the user may switch languages (or a new language file may become avail
    // in the future that matches closer to their locale)
    if (iUsingLocale >= 0)
    	((List)locale_param.getControl()).select(iUsingLocale);

    locale_param.addChangeListener( new ParameterChangeAdapter() {
      @Override
      public void parameterChanged(Parameter p, boolean caused_internally ) {
		MessageText.loadBundle();
        DisplayFormatters.setUnits();
        DisplayFormatters.loadMessages();
        UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
        if (uiFunctions != null) {
        	uiFunctions.refreshLanguage();
        }
      }
    });

    BooleanParameter uc = new BooleanParameter( cMain, "label.lang.upper.case", false, "label.lang.upper.case" );

    uc.addChangeListener( new ParameterChangeAdapter() {
        @Override
        public void parameterChanged(Parameter p, boolean caused_internally ) {
    		MessageText.loadBundle(true);
            DisplayFormatters.setUnits();
            DisplayFormatters.loadMessages();
            UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
            if (uiFunctions != null) {
            	uiFunctions.refreshLanguage();
            }
        }
    });

    return cMain;
  }

}
