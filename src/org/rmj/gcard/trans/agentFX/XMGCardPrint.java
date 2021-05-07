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
public class XMGCardPrint {
    public XMGCardPrint(GRider foGRider, String fsBranchCd){
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
        if(fsFieldNm.equalsIgnoreCase("sApplicNo")){
            String lsPrefix = "";
            if(fsValue.trim().length() <= 0 || fsValue.contains("%"))
                lsPrefix = "";
            else if(fsValue.length() <= 6)
                lsPrefix = psBranchCd + SQLUtil.dateFormat(poGRider.getSysDate(), "yy");
            else if(fsValue.length() <= 8)
                lsPrefix = psBranchCd;

            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same transaction code!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sApplicNo LIKE " + SQLUtil.toSQL(lsPrefix + "%" + fsValue));
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
            setMessage("Invalid connection detected. Please inform SSG/SSE for help!");
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
                                                                "Name»Address»Application No.»Activated", 
                                                                "sClientNm»xAddressx»a.sApplicNo»dTransact");
              
                if (loValue != null){
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
                    "  a.sApplicNo" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", a.dActivate dTransact" +
                    ", a.sGCardNox" +
                " FROM G_Card_Master a" +
                    ", Client_Master b" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                " WHERE a.sClientID = b.sClientID" +
                    " AND a.cCardStat = " + SQLUtil.toSQL(GCardStatus.NEW);
        
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

    public XMGCApplication getApplication(){
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
            setMessage("No Record was loaded!");
           return false;
        }
        else{
            //set the values of foreign key(object) to null
            poClntx = null;
            poAppxx = null;
            poBrnch = null;

            if(poData.getDigital().equalsIgnoreCase("1")){
                poData = null;
                pnEditMode = EditMode.UNKNOWN;
                setMessage("Digital GCard can't be printed.");
                return false;
            }
         
            pnEditMode = EditMode.READY;
            return true;
        }
    }

    public boolean print(){
        boolean lbCancel = false;
        boolean lbCardOk = false;
        String lsSerial;

        //Validate if record is loaded
        if(pnEditMode != EditMode.READY){
            setMessage("No Record was loaded!");
           return false;
        }

        //TODO: Make sure that either user inserts the card to the printer or
        //user cancels this process
        while(lbCancel == false && lbCardOk == false){
           lbCardOk = true;
        }

        //If user presses cancel
        if(lbCancel == true){
            setMessage("Process cancelled!");
           return false;
        }

        //TODO: Get the Serial of the Card
        //Note: Serial will be save in the history of the GCard
        lsSerial = "";

        poGRider.beginTrans();
        //issue the print method to record this process
        lbCardOk = poCtrl.print(poData.getGCardNo(), lsSerial, poTranDate);

        //If method invoke returns false inform the user
        if(!lbCardOk){
            setMessage(poCtrl.getErrMsg() + poCtrl.getMessage());
            poGRider.rollbackTrans();
            return false;
        }

        //Inform the user that the process was executed successfully
        setMessage("Card successfully tagged as printed!");
      
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
}
