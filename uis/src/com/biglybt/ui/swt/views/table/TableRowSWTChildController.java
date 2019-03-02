package com.biglybt.ui.swt.views.table;

public interface 
TableRowSWTChildController
{
	public boolean
	isExpanded();
	
	public void
	setExpanded(
		boolean	expanded );
	
	public Object[]
	getChildDataSources();
}
