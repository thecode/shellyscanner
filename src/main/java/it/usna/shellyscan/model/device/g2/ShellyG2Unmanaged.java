package it.usna.shellyscan.model.device.g2;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import it.usna.shellyscan.model.device.ShellyUnmanagedDevice;

public class ShellyG2Unmanaged extends AbstractG2Device implements ShellyUnmanagedDevice {
	private String type;
	private Exception ex;

	public ShellyG2Unmanaged(InetAddress address, int port, String hostname) {
		super(address, port, hostname);
	}
	
	public ShellyG2Unmanaged(InetAddress address, int port, String hostname, Exception e) {
		super(address, port, hostname);
		this.ex = e;
		this.hostname = hostname;
		name = "";
		if(e instanceof IOException && "Status-401".equals(e.getMessage())) {
			status = Status.NOT_LOOGGED;
		} else if(e instanceof IOException && e instanceof JsonProcessingException == false) { // JsonProcessingException extends IOException
			status = Status.OFF_LINE;
		} else {
			status = Status.ERROR;
		}
	}
	
	@Override
	protected void init(JsonNode devInfo) throws IOException {
		try {
//			JsonNode device = getJSON("/rpc/Shelly.GetDeviceInfo");
			this.type = devInfo.get("app").asText();
			this.mac = devInfo.get("mac").asText();
			this.hostname = devInfo.get("id").asText("");

//			fillOnce(device);
			fillSettings(getJSON("/rpc/Shelly.GetConfig"));
			fillStatus(getJSON("/rpc/Shelly.GetStatus"));
		} catch (/*IO*/Exception e) {
			if(status != Status.NOT_LOOGGED) {
				status = Status.ERROR;
			}
			this.ex = e;
			name = "";
		}
	}
	
	public String getTypeName() {
		return "Generic G2";
	}
	
	@Override
	public String getTypeID() {
		return type;
	}
	
	/**
	 * @return null if device type is unknown or exception if an error ha occurred on construction 
	 */
	@Override
	public Exception geException() {
		return ex;
	}
	
	@Override
	protected void restore(JsonNode settings, ArrayList<String> errors) throws IOException {
		// basic restore? not in case of error
	}
	
	@Override
	public Status getStatus() {
		if(status == Status.ON_LINE && ex != null) {
			return Status.ERROR;
		} else {
			return status;
		}
	}

	@Override
	public String toString() {
		if(ex == null) {
			return "Shelly G2 (unmanaged) " + type + ": " + super.toString();
		} else {
			return "Shelly G2 (unmanaged): " + super.toString() + " Error: " + ex.getMessage();
		}
	}
}