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
import java.util.Date;
import org.json.simple.JSONObject;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.constants.GCardStatus;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.replication.utility.LogWrapper;
import org.rmj.client.agent.XMClient;
import org.rmj.gcard.trans.GCard;
import org.rmj.gcard.base.misc.GCEncoder;
import org.rmj.integsys.pojo.UnitGCard;
import org.rmj.parameters.agent.XMBranch;

/**
 *
 * @author kalyptus
 */
public class XMGCardActivate {
    public XMGCardActivate(GRider foGRider, String fsBranchCd){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCard();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);
            pnEditMode = EditMode.UNKNOWN;

            poData = new UnitGCard();
            poTranDate = foGRider.getServerDate();
            
            ClearGCardProperty();
        }
    }

    public boolean searchWithCondition(String fsFieldNm, String fsValue, String fsFilter){
        System.out.println("Inside SearchWithCondition");
        fsValue = fsValue.trim();
        
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!");
            return false;
        }

        String lsSQL = getSearchSQL();
        if(fsFieldNm.equalsIgnoreCase("sCardNmbr")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same card number!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sCardNmbr LIKE " + SQLUtil.toSQL(fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sClientNm")){
            String lsValue = ((String) poData.getValue("sLastname")) + ", " +
                             ((String) poData.getValue("sFrstName")) + " " +
                             ((String) poData.getValue("sMiddName"));
            if(fsValue.trim().equalsIgnoreCase(lsValue.trim())){
                setMessage("The same client name!");
                return false;
            }
            
            lsSQL = MiscUtil.addCondition(lsSQL, "b.sCompnyNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
            
            //lsSQL = MiscUtil.addCondition(lsSQL, "CONCAT(b.sLastName, ', ', b.sFrstName, IF(IFNULL(b.sSuffixNm, '') = '', CONCAT(' ', b.sSuffixNm), ' '), b.sMiddName) LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        if(!fsFilter.isEmpty()){
            lsSQL = MiscUtil.addCondition(lsSQL, fsFilter);
        }

        System.out.println(lsSQL);

        //Create the connection object
        Connection loCon = poGRider.getConnection();

        if(loCon == null){
            setMessage("Invalid connection!");
            return false;
        }

        boolean lbHasRec = false;
        Statement loStmt = null;
        ResultSet loRS = null;

        try {
            System.out.println("Before Execute");

            loStmt = loCon.createStatement();
            loRS = loStmt.executeQuery(lsSQL);

            ClearGCardProperty();
            
            if(!loRS.next())
                setMessage("No Record Found!");
            else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Name»Address»Activated»Card Number", 
                                                                "sClientNm»xAddressx»dTransact»a.sCardNmbr");
              
                if (loValue != null){
                    System.setProperty("app.gcard.no", (String) loValue.get("sGCardNox"));
                    System.setProperty("app.card.no", (String) loValue.get("sCardNmbr"));
                    System.setProperty("app.gcard.holder", (String) loValue.get("sClientNm"));
                    System.setProperty("app.client.id", (String) loValue.get("sClientID"));
                    System.setProperty("app.device.type", (String) loValue.get("cDigitalx"));
                    
                    setMessage("GCard" + (String) loValue.get("sGCardNox"));
                    lbHasRec = loadTransaction((String) loValue.get("sGCardNox"));
                } else
                    setMessage("No record selected...");
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
                    "  a.sCardNmbr" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", a.dActivate dTransact" +
                    ", a.sGCardNox" +
                    ", a.cCardStat" + 
                    ", b.sMobileNo" +
                    ", a.sClientID" +
                    ", a.cDigitalx" +
                " FROM G_Card_Master a" +
                    ", Client_Master b" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                " WHERE a.sClientID = b.sClientID" +
                    " AND a.cCardStat = " + SQLUtil.toSQL(GCardStatus.ISSUED);
        
        //CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')), b.sMiddName)
    }
   
    public int getEditMode(){
        return pnEditMode;
    }
   
    public void setTranDate(Date loDate){
        poTranDate = loDate;
    }

    public Date getTranDate(){
        return poTranDate;
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

    public boolean connectCard(){
        if(!GCEncoder.init()){
            logwrapr.severe("connectCard: Can't Initialize card. " + GCEncoder.getErrMessage());
            setMessage("connectCard: Can't Initialize card. " + GCEncoder.getErrMessage());
            return false;
        }

        if(!GCEncoder.connect()){
            logwrapr.severe("connectCard: Can't Connect card. " + GCEncoder.getErrMessage());
            setMessage("connectCard: Can't Connect card. " + GCEncoder.getErrMessage());         
            return false;
        }

        String lsGCardNmbr = (String) GCEncoder.read(GCEncoder.CARD_NUMBER);

        return searchWithCondition("sCardNmbr", lsGCardNmbr, "");
    }

    public boolean releaseCard(){
        if(!GCEncoder.disconnect()){
            setMessage(GCEncoder.getErrMessage());
            return false;
        }

        return loadTransaction("");
    }

    public XMClient getClient(){
        if(poClntx == null)
            poClntx = new XMClient(poGRider, psBranchCd, true);

        poClntx.openRecord(poData.getClientID());
        return poClntx;
    }

    public XMBranch getBranch(String sBranchCd){
        if(poBrnch == null)
            poBrnch = new XMBranch(poGRider, sBranchCd, true); //psBranchCd

        poBrnch.openRecord(poData.getCompanyID());
        return poBrnch;
    }

    public XMGCApplication getApplication(String sBranchCd){
        if(poAppxx == null)
            poAppxx = new XMGCApplication(poGRider, psBranchCd, true);

        poAppxx.loadTransaction(poData.getApplicNo());
        return poAppxx;
    }

    public boolean loadTransaction(String fsTransNox){
        //Try to open the record
        poData = (UnitGCard) poCtrl.openRecord(fsTransNox);

        if(poData.getGCardNo() == null){
           //Inform that no record was opened
           pnEditMode = EditMode.UNKNOWN;
        }
        else{
            if(poData.getDigital().equalsIgnoreCase("1")){
                poData = null;
                pnEditMode = EditMode.UNKNOWN;
                setMessage("Digital GCard can't be printed.");
                return false;
            }
          
            //set the values of foreign key(object) to null
            pnEditMode = EditMode.READY;
        }

        poClntx = null;
        poAppxx = null;
        poBrnch = null;
        return (poData.getGCardNo() != null);

    }

    public boolean activateNonChip(){
        if (!poCtrl.activate(poData.getGCardNo(),  poTranDate)){
            setMessage(poCtrl.getMessage());
            return false;
        }
        setMessage("GCard activated successfully!");
        return true;
    }
    
    public boolean activate(){
        boolean lbCancel = false;
        boolean lbCardOk = false;

        //Validate if record is loaded
        if(pnEditMode != EditMode.READY){
            setMessage("No Record was loaded!");
            return false;
        }

        //TODO: Make sure that either user inserts the card or
        //user cancels this process
        while(lbCancel == false && lbCardOk == false){
           lbCardOk = true;
        }

        //If user presses cancel
        if(lbCancel == true){
            setMessage("Process cancelled!");
            return false;
        }

        String lsGCardNmbr = (String) GCEncoder.read(GCEncoder.CARD_NUMBER);
      
        if(!lsGCardNmbr.equals(poData.getCardNumber())){
            setMessage("Card seems not loaded!");
            logwrapr.severe("activate: Card seems not loaded..." + lsGCardNmbr  );      
            return false;
        }       
      
        poGRider.beginTrans();
        //issue the activate method to record this process
        lbCardOk = poCtrl.activate(poData.getGCardNo(),  poTranDate);

        //If method invoke returns false inform the user
        if(!lbCardOk){
            if(!poCtrl.getErrMsg().isEmpty())
                setMessage(poCtrl.getErrMsg());
            else if(!poCtrl.getMessage().isEmpty())
                setMessage(poCtrl.getMessage());
            else
                setMessage("Unknown error encountered!");

            poGRider.rollbackTrans();
            return false;
        }

        setMessage("GCard activated successfully!");

        poGRider.commitTrans();  
        return true;
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

    public void setGRider(GRider foGRider) {
        this.poGRider = foGRider;
        poCtrl.setGRider(foGRider);
    }
   
    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}

    private UnitGCard poData;
    private GCard poCtrl;
    private GRider poGRider;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;

    private static LogWrapper logwrapr = new LogWrapper("XMGCardActivate", "temp/XMGCardActivate.log");

    private XMClient poClntx = null;
    private XMBranch poBrnch = null;
    private XMGCApplication poAppxx = null;

    private Date poTranDate = null;
}
