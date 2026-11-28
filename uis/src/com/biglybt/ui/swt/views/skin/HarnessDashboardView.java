/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.biglybt.ui.swt.views.skin;

import org.eclipse.swt.widgets.Composite;

import com.biglybt.ui.swt.skin.SWTSkinObject;

/**
 * Replaces the legacy dashboard builder (and its embedded wiki tile) with the
 * native Harness landing surface. Attached to the "dashboard" skin object, so
 * it is built whenever the sidebar's Dashboard header is selected.
 */
public class HarnessDashboardView
	extends SkinView
{
	private HarnessHomePanel panel;

	@Override
	public Object
	skinObjectInitialShow(
		SWTSkinObject	skinObject,
		Object			params )
	{
		SWTSkinObject area = getSkinObject( "dashboard-area" );

		if ( area != null && area.getControl() instanceof Composite ){
			panel = new HarnessHomePanel((Composite)area.getControl());
		}

		return( null );
	}

	@Override
	public Object
	skinObjectDestroyed(
		SWTSkinObject	skinObject,
		Object			params )
	{
		panel = null;

		return( super.skinObjectDestroyed( skinObject, params ));
	}
}
