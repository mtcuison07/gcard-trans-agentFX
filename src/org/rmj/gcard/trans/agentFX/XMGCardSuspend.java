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
import org.rmj.client.agent.XMClient;
import org.rmj.gcard.trans.GCard;
import org.rmj.integsys.pojo.UnitGCard;
import org.rmj.parameters.agent.XMBranch;

/**
 *
 * @author kalyptus
 */
public class XMGCardSuspend {
    public XMGCardSuspend(GRider foGRider, String fsBranchCd){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCard();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);
            pnEditMode = EditMode.UNKNOWN;

            poData = new UnitGCard();
            poTranDate = foGRider.getSysDate();
            psRemarksx = "";
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

            if(!loRS.next())
                setMessage("No Record Found!");
            else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Name»Address»Card Number»Activated", 
                                                                "sClientNm»xAddressx»a.sCardNmbr»dTransact");
              
                if (loValue != null){
                    System.out.println("GCard" + (String) loValue.get("sGCardNox"));
                    lbHasRec = loadTransaction((String) loValue.get("sGCardNox"));
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
                    "  a.sCardNmbr" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", a.dActivate dTransact" +
                    ", a.sGCardNox" +
                " FROM G_Card_Master a" +
                    ", Client_Master b" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                " WHERE a.sClientID = b.sClientID" +
                    " AND a.cCardStat = " + SQLUtil.toSQL(GCardStatus.ACTIVATED);
        
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

    public void setRemarks(String foString){
        psRemarksx = foString;
    }

    public String getRemarks(){
        return psRemarksx;
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

    public XMClient getClient(){
        if(poClntx == null)
            poClntx = new XMClient(poGRider, psBranchCd, true);

        poClntx.openRecord(poData.getClientID());
        return poClntx;
    }

    public XMBranch getBranch(String sBranchCd){
        if(poBrnch == null)
            poBrnch = new XMBranch(poGRider, psBranchCd, true);

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

        if(poCtrl == null){
            pnEditMode = EditMode.UNKNOWN;
            return false;
        }
      
        poData = (UnitGCard) poCtrl.openRecord(fsTransNox);
      
        if(poData.getGCardNo() == null){
            //Inform that no record was opened
            pnEditMode = EditMode.UNKNOWN;
            if(!fsTransNox.equalsIgnoreCase("x")) 
                setMessage(poCtrl.getErrMsg() + poCtrl.getMessage());
                return false;
        }
        else{
            //set the values of foreign key(object) to null
            poClntx = null;
            poAppxx = null;
            poBrnch = null;
            pnEditMode = EditMode.READY;
            return true;
        }
    }

    public boolean deactivate(){
        boolean lbCardOk = false;

        //Validate if record is loaded
        if(pnEditMode != EditMode.READY){
            setMessage("No Record was loaded!");
            return false;
        }

        poGRider.beginTrans();
      
        //issue the suspend method to record this process
        lbCardOk = poCtrl.suspend(poData.getGCardNo(), psRemarksx, poTranDate);

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

        setMessage("GCard suspended successfully!");

        poGRider.commitTrans();
        return true;
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

    private XMClient poClntx = null;
    private XMBranch poBrnch = null;
    private XMGCApplication poAppxx = null;

    private Date poTranDate = null;
    private String psRemarksx = null;
}
