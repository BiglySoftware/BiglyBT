package com.biglybt.core.download;

public interface 
DownloadManagerOptionsHandler
{
	public String
	getName();
	
	public int
	getUploadRateLimitBytesPerSecond();
	
	public void
	setUploadRateLimitBytesPerSecond(
		int		limit );
	
	public int
	getDownloadRateLimitBytesPerSecond();
	
	public void
	setDownloadRateLimitBytesPerSecond(
		int		limit );

	public int
	getIntParameter(
		String		name );
	
	public void
	setIntParameter(
		String		key,
		int			value );
	
	public boolean
	getBooleanParameter(
		String		name );
	
	public void
	setBooleanParameter(
		String		key,
		boolean		value );
	
	public void
	setParameterDefault(
		String		key );
	
	public DownloadManager
	getDownloadManager();
	
	public void
	addListener(
		ParameterChangeListener	listener );
	
	public void
	removeListener(
		ParameterChangeListener	listener );
	
	public interface
	ParameterChangeListener
	{
		public void
		parameterChanged(
			DownloadManagerOptionsHandler		handler );
	}
}
