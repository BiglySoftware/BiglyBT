package com.biglybt.ui.swt.pif;

import java.util.List;

import com.biglybt.pif.PluginInterface;

public interface 
UISWTViewEventListenerEx
	extends UISWTViewEventListener
{
	public UISWTViewEventListenerEx
	getClone();
	
	public default CloneConstructor
	getCloneConstructor()
	{
		return( null );
	}
	
	public interface
	CloneConstructor
	{
		public PluginInterface
		getPluginInterface();
		
		public String
		getIPCMethod();
		
		public default List<Object>
		getIPCParameters()
		{
			return( null );
		}
	}
}	
