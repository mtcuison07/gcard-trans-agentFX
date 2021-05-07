/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rmj.gcard.trans.agentFX;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import org.json.simple.JSONObject;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.appdriver.constants.GCDeviceType;
import org.rmj.client.agent.XMClient;
import org.rmj.gcard.trans.GCInquiry;
import org.rmj.gcard.base.misc.GCPointBasis;
import org.rmj.gcard.device.ui.GCardDevice;
import org.rmj.gcard.device.ui.GCardDeviceFactory;
import org.rmj.integsys.pojo.UnitBranch;
import org.rmj.integsys.pojo.UnitGCHistory;
import org.rmj.integsys.pojo.UnitGCardDetailOffline;
import org.rmj.integsys.pojo.UnitGCPointsBasis;
import org.rmj.integsys.pojo.UnitGCard;
import org.rmj.parameters.Branch;
//import org.rmj.parameters.Branch;

/**
 *
 * @author kalyptus
 */

/* Note:
 *    GRider past to this object should be set to online mode.
 */
public class XMGCInquiry{
    public XMGCInquiry(GRider foGRider, String fsBranchCd, boolean fbWithParent){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.pbWithParnt = fbWithParent;
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCInquiry();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            pnEditMode = EditMode.UNKNOWN;

            poData = new UnitGCard();
        }
    }

    public Object getMaster(String fsCol){
        return getMaster(poData.getColumn(fsCol));
    }

    public Object getMaster(int fnCol) {
        if(pnEditMode == EditMode.UNKNOWN || poCtrl == null)
            return null;
        else{
            return poData.getValue(fnCol);
        }
    }

    public Object getDetail(int row, String fsCol){
        return getDetail(row, offline.get(row).getColumn(fsCol));
    }

    public Object getDetail(int row, int col){
        if(pnEditMode == EditMode.UNKNOWN || poCtrl == null)
            return null;
        else if(row < 0 || row >= offline.size())
            return null;

        return offline.get(row).getValue(col);
    }

    public Object getHistory(int row, String fsCol){
        return getHistory(row, history.get(row).getColumn(fsCol));
    }   

    public Object getHistory(int row, int col){
        if(pnEditMode == EditMode.UNKNOWN || poCtrl == null)
            return null;
        else if(row < 0 || row >= history.size())
            return null;

        return history.get(row).getValue(col);
    }

    public XMClient getClient(){
        if(poClntx == null)
            poClntx = new XMClient(poGRider, psBranchCd, true);

        poClntx.openRecord(poData.getClientID());
        return poClntx;
    }
   
    public Object getDeviceInfo(){
        if(poGCDevice == null){
           return null;
        }

        return poGCDevice;
    }

    public XMGCOnPoints getLastOnline(){
        if(poOnline == null)
            poOnline = new XMGCOnPoints(poGRider, psBranchCd, true);

        poOnline.loadTransaction(poData.getLastLine());
        return poOnline;
    }

    public UnitBranch getBranch(String sBranchCd){
        Branch loBranch = new Branch();
        loBranch.setGRider(poGRider);
        loBranch.setBranch(psBranchCd);
        loBranch.setWithParent(true);
        return loBranch.openRecord(sBranchCd);
    }

    public  UnitGCPointsBasis getSource(String sSourceCd){
        GCPointBasis loSource = new GCPointBasis();
        loSource.setGRider(poGRider);
        loSource.setBranch(psBranchCd);
        loSource.setWithParent(true);
        return loSource.openRecord(sSourceCd);
    }
   
    public boolean loadLastFromCard(){
        //GCardDeviceFactory.DeviceType cardtype
        /*poGCDevice = GCardDeviceFactory.make(cardtype);
        poGCDevice.setGRider(poGRider);
        
        if(!poGCDevice.read()){
            setMessage(poGCDevice.getMessage());
            return false;
        }*/

        String lsGCardNmbr = (String) poGCDevice.getCardInfo("sCardNmbr"); 
        XMGCard gcard = new XMGCard(poGRider, psBranchCd, true);
        if(!gcard.searchWithCondition("sCardNmbr" , lsGCardNmbr , "")){
            setMessage("Unable to load last transaction!");
            return false;
        }

        //return loadTransaction((String)gcard.getMaster("sLastLine"));
        
        return loadTransaction((String)gcard.getMaster("sGCardNox"));
    }
    
    public boolean loadTransaction(String fsTransNox){
        if(poCtrl == null){
            return false;
        }

        poData = poCtrl.loadTransaction(fsTransNox);

        if(poData.getCardNumber() == null){
            return false;
        }
        else{
            //set the values of foreign key(object) to null
            poClntx = null;
            poOnline = null;

            this.offline = new ArrayList<UnitGCardDetailOffline>();
            this.history = new ArrayList<UnitGCHistory>();

            pnEditMode = EditMode.READY;
            return true;
        }
    }

    public boolean loadOffLineLedger(){
        System.out.println("XMGInquiry.loadOfflineLedger");
        if(poCtrl == null)
            return false;

        if(poData.getGCardNo() == null)      
            return false;
        
        if(this.offline == null)
            this.offline = new ArrayList<>();
        
        if(!this.offline.isEmpty())
            this.offline = new ArrayList<>();
        
        //ArrayList<UnitGCardDetailOffline> offline = poCtrl.loadOffLineLedger((String) poData.getGCardNo());        
        ArrayList<UnitGCardDetailOffline> offline = poCtrl.loadOffLineLedger(poData.getCardNumber());        
                
        if (offline != null) this.offline.addAll(offline);
      
        return true;
    }

    public boolean loadHistoryLedger(){
        if(poCtrl == null)
            return false;

        if(poData.getCardNumber() == null)
            return false;

        if(!this.history.isEmpty())
            this.history = new ArrayList<UnitGCHistory>();

        history.addAll(poCtrl.loadHistoryLedger(poData.getCardNumber()));
      
        return true;
    }

    public boolean connectCard(){
        if (null == GCardDeviceFactory.DeviceTypeID(System.getProperty("app.device.type"))){
            poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.SMARTCARD);
            System.setProperty("app.device.type", GCDeviceType.SMARTCARD);
        } else
            switch (GCardDeviceFactory.DeviceTypeID(System.getProperty("app.device.type"))) {
                case SMARTCARD:
                    poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.SMARTCARD);
                    System.setProperty("app.device.type", GCDeviceType.SMARTCARD);
                    break;
                case NONE:
                    poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.NONE);
                    poGCDevice.setCardNo(System.getProperty("app.card.no"));
                    System.setProperty("app.device.type", GCDeviceType.NONE);
                    break;
                case QRCODE:
                    poGCDevice = GCardDeviceFactory.make(GCardDeviceFactory.DeviceType.QRCODE);
                    poGCDevice.setCardNo(System.getProperty("app.card.no"));
                    System.setProperty("app.device.type", GCDeviceType.QRCODE);
                    break;
                default:
                    setMessage("Invalid Device Type.");
                    return false;
        }
        
        poGCDevice.setGRider(poGRider);
        
        if (poGCDevice.read()){
            System.setProperty("app.gcard.no", (String) poGCDevice.getCardInfo("sGCardNox"));
            System.setProperty("app.gcard.holder", (String) poGCDevice.getCardInfo("sCompnyNm"));
            System.setProperty("app.card.no", (String) poGCDevice.getCardInfo("sCardNmbr"));
            System.setProperty("app.gcard.mobile", (String) poGCDevice.getCardInfo("sMobileNo"));
            System.setProperty("app.device.data", poGCDevice.getCardInfo().toJSONString());
            System.setProperty("app.card.connected", "1");
            System.setProperty("app.gcard.online", String.valueOf(poGCDevice.getCardInfo("bIsOnline")));
            System.setProperty("app.client.id", (String) poGCDevice.getCardInfo("sClientID"));           
            
            poData.setGCardNo((String)poGCDevice.getCardInfo("sGCardNox"));
            return true;
        } else {
            System.setProperty("app.card.connected", "0");
            setMessage(poGCDevice.getMessage());
            return false;
        }        
    }

    public XMGCard getGCard(){
        if(poGCard == null)
            poGCard = new XMGCard(poGRider, psBranchCd, true);

        poGCard.openRecord(poData.getGCardNo());
        return poGCard;
    }
    
    public boolean releaseCard(){
        if(!poGCDevice.release()){
            setMessage(poGCDevice.getMessage());
            return false;
        }

        poData = new UnitGCard();
        return true;
    }

    public boolean searchWithCondition(String fsFieldNm, String fsValue, String fsFilter){
        System.out.println("Inside SearchWithCondition");
        fsValue = fsValue.trim();
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!");            
            return false;
        }

        String lsSQL = getSearchSQL();
        if(fsFieldNm.equalsIgnoreCase("sGCardNox")){
            String lsPrefix = "";
            if(fsValue.trim().length() <= 0 || fsValue.contains("%"))
                lsPrefix = "";
            else if(fsValue.length() <= 6)
                lsPrefix = psBranchCd + SQLUtil.dateFormat(poGRider.getSysDate(), "yy");
            else if(fsValue.length() <= 8)
                lsPrefix = psBranchCd;

            if(pnEditMode != EditMode.UNKNOWN){
                if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                    setMessage("The same transaction code!");            
                    return false;
                }
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sGCardNox LIKE " + SQLUtil.toSQL(lsPrefix + "%" + fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sCardNmbr")){
            if(pnEditMode != EditMode.UNKNOWN){
                if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                    setMessage("The same card number!");
                    return false;
                }
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sCardNmbr LIKE " + SQLUtil.toSQL(fsValue.replace("-", "")));
        }
        else if(fsFieldNm.equalsIgnoreCase("sClientNm")){
            if(pnEditMode != EditMode.UNKNOWN){
                String lsValue = ((String) poData.getValue("sLastname")) + ", " +
                                    ((String) poData.getValue("sFrstName")) + " " +
                                    ((String) poData.getValue("sMiddName"));
                if(fsValue.trim().equalsIgnoreCase(lsValue.trim())){
                    setMessage("The same client name!");
                    return false;
                }
            }
            
            lsSQL = MiscUtil.addCondition(lsSQL, "b.sCompnyNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
            //lsSQL = MiscUtil.addCondition(lsSQL, "CONCAT(b.sLastName, ', ', b.sFrstName, IF(IFNULL(b.sSuffixNm, '') = '', CONCAT(' ', b.sSuffixNm), ' '), b.sMiddName) LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        if(!fsFilter.isEmpty()){
            lsSQL = MiscUtil.addCondition(lsSQL, fsFilter);
        }

        System.out.println(lsSQL);

        //Create the connection object
        Connection loCon = null;
        loCon = poGRider.getConnection();

        System.out.println("After doCon");

        if(loCon == null){
            setMessage("Invalid connection!");
            return false;
        }

        boolean lbHasRec = false;
        Statement loStmt = null;
        ResultSet loRS = null;

        try {
            System.out.println("Before Execute");

            //mac 2020-07-21
            //validate selected card type
            if (!System.getProperty("app.device.type").isEmpty()){
                lsSQL = MiscUtil.addCondition(lsSQL, "a.cDigitalx = " + SQLUtil.toSQL(System.getProperty("app.device.type")));
            }
            
            loStmt = loCon.createStatement();
            loRS = loStmt.executeQuery(lsSQL);
            
            ClearGCardProperty();

            if(!loRS.next())
                setMessage("No Record Found!");
            else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Client Name»Address»Card Number", 
                                                                "sClientNm»xAddressx»sCardNmbr");
              
                if (loValue != null){
                    lbHasRec = loadTransaction((String) loValue.get("sGCardNox"));
                    
                    //connect card
                    if (lbHasRec){
                        System.setProperty("app.gcard.no", (String) loValue.get("sGCardNox"));
                        System.setProperty("app.card.no", (String) loValue.get("sCardNmbr"));
                        System.setProperty("app.gcard.holder", (String) loValue.get("sClientNm"));
                        System.setProperty("app.client.id", (String) loValue.get("sClientID"));
                        System.setProperty("app.device.type", (String) loValue.get("cDigitalx"));
                        
                        if (!"0".equals((String) loValue.get("cDigitalx"))) 
                            lbHasRec = connectCard();
                        else{
                            setMessage("Client was a SMARTCARD holder. Please use his SMARTCARD to CONNECT.");
                            lbHasRec = false;
                        }
                    }
                } else {
                    setMessage("No record selected...");
                }
            }
            System.out.println("After Execute");
        } catch (SQLException ex) {
            ex.printStackTrace();
            setMessage(ex.getMessage());
        }
        finally{
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
        }

        return lbHasRec;
    }

    public String getSearchSQL(){                
        return "SELECT" +
                    "  a.sGCardNox" +
                    ", a.sCardNmbr" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", a.sClientID" +
                    ", a.cDigitalx" +
                " FROM G_Card_Master a" +
                    ", Client_Master b" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                " WHERE a.sClientID = b.sClientID";
        
                //CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')),  b.sMiddName)
    }
    
    public void ClearGCardProperty(){
        System.setProperty("app.card.connected", "");
        System.setProperty("app.gcard.no", "");
        System.setProperty("app.gcard.holder", "");
        System.setProperty("app.card.no", "");
        System.setProperty("app.device.type", "");
        System.setProperty("app.device.data", "");
        System.setProperty("app.client.id", "");
        System.setProperty("app.gcard.online", "");
    } 

    public void setBranch(String fsBranchCd) {
        psBranchCd = fsBranchCd;
        poCtrl.setBranch(psBranchCd);
    }

    public int getEditMode() {
        return pnEditMode;
    }

    public int getOffLineSize(){
        if(offline == null)
           return 0;
        
        return offline.size();
    }

    public int getHistorySize(){
        if(history == null)
            return 0;
        
        return history.size();
    }

    public void setGRider(GRider foGRider) {
        this.poGRider = foGRider;
        poCtrl.setGRider(foGRider);
    }
   
    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}
   
    private XMGCard poGCard = null;
   
    private UnitGCard poData;
    private GCInquiry poCtrl;
    private GRider poGRider;
    private GCardDevice poGCDevice;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;

    private XMClient poClntx = null;
    public XMGCOnPoints poOnline = null;

    ArrayList <UnitGCardDetailOffline> offline;
    ArrayList <UnitGCHistory> history;
    private boolean pbWithParnt = false;
}
