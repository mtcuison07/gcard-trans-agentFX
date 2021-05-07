/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rmj.gcard.trans.agentFX;

import org.rmj.appdriver.GRider;
import org.rmj.appdriver.agentfx.callback.IMaster;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.gcard.device.ui.GCardConnect;
import org.rmj.gcard.device.ui.GCardDeviceFactory;

/**
 *
 * @author kalyptus
 */
public class XMGCConnect{
    private GRider oApp;
    private String sMessage;
    private int nEditMode;
    
    private GCardConnect oTrans;
    private GCardDeviceFactory.DeviceType nDeviceType;
    
    private boolean bConnected;
    private IMaster poCallBack;
    
    public XMGCConnect(GRider foGRider){
        oApp = foGRider;
        
        if (oApp != null){
            oTrans = new GCardConnect(oApp);

            bConnected = false;
            nDeviceType = GCardDeviceFactory.DeviceType.NONE;
            nEditMode = EditMode.UNKNOWN;
        }
    }
    
    public boolean Connect(){
        oTrans.setDeviceType(nDeviceType);
        
        bConnected = oTrans.Connect();
        
        if (!bConnected){
            setMessage(oTrans.getMessage());
            return false;
        }
        
        setMessage("Connection was set successfully.");
        return true;
    }
    
    public boolean Disconnect(){
        bConnected = oTrans.Disconnect();
        
        if (!bConnected){
            setMessage(oTrans.getMessage());
            return false;
        }
        
        setMessage("Connection was disconnected successfully.");
        return true;
    }
    
    public boolean SearchMaster(String fsIndex, Object foValue){
        return oTrans.SearchMaster(fsIndex, foValue);
    }
    
    private void MasterRetreived(int fnRow){
        if (poCallBack == null) return;
        
        poCallBack.MasterRetreive(fnRow);
    }
    
    
    public int getEditMode(){return nEditMode;}
    public String getMessage(){return sMessage;}
    public String getGCardNox(){return bConnected ? System.getProperty("app.gcard.no") : "";}
    public String getCardNox(){return bConnected ? System.getProperty("app.card.no") : "";}
    
    private void setMessage(String fsValue){sMessage = fsValue;}
    
    public void setDeviceType(GCardDeviceFactory.DeviceType fnValue){
        nDeviceType = fnValue;
        oTrans.setDeviceType(nDeviceType);
        MasterRetreived(80);
    }
    public GCardDeviceFactory.DeviceType getDeviceType(){return nDeviceType;}
    
    public boolean IsConnected(){return bConnected;}
}

