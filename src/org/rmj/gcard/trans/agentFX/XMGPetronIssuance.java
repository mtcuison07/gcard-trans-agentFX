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
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.client.agent.XMClient;
import org.rmj.gcard.trans.GCard;
import org.rmj.gcard.base.misc.GCEncoder;
import org.rmj.integsys.pojo.UnitGCard;
import org.rmj.parameters.agent.XMBranch;

/**
 *
 * @author kalyptus
 */
//TODO: Make sure that the GCard is located in the issuing branch.
public class XMGPetronIssuance {
    public XMGPetronIssuance(GRider foGRider, String fsBranchCd){
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
            System.out.println("Nothing to process!");
            return false;
        }

        String lsSQL = getSearchSQL();
        if(fsFieldNm.equalsIgnoreCase("sCardNmbr")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                System.out.println("The same card number!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sCardNmbr LIKE " + SQLUtil.toSQL(fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sClientNm")){
            String lsValue = ((String) poData.getValue("sLastname")) + ", " +
                             ((String) poData.getValue("sFrstName")) + " " +
                             ((String) poData.getValue("sMiddName"));
            if(fsValue.trim().equalsIgnoreCase(lsValue.trim())){
                System.out.println("The same client name!");
                return false;
            }

            //mac 2019.07.01
            //String []laNames = MiscUtil.splitName(fsValue);
            //lsSQL = MiscUtil.addCondition(lsSQL, "b.sLastName LIKE " + SQLUtil.toSQL(laNames[0]) +
            //                                    " AND CONCAT(b.sFrstname, IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(' ', b.sSuffixNm))) LIKE " + SQLUtil.toSQL(laNames[1]) +
            //                                   " AND b.sMiddName LIKE " + SQLUtil.toSQL(laNames[2]));
            
            lsSQL = MiscUtil.addCondition(lsSQL, "CONCAT(b.sLastName, ', ', b.sFrstName, IF(IFNULL(b.sSuffixNm, '') = '', CONCAT(' ', b.sSuffixNm), ' '), b.sMiddName) LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        if(!fsFilter.isEmpty()){
            lsSQL = MiscUtil.addCondition(lsSQL, fsFilter);
        }

        System.out.println(lsSQL);

        //Create the connection object
        Connection loCon = poGRider.getConnection();

        if(loCon == null){
            System.out.println("Invalid connection!");
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
                setMessage("No Petron Value Card Redemption was found for this GCard!");
            else{
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Card Number»Client Name»Address»Activated»Petron No»Remarks", 
                                                                "a.sCardNmbr»sClientNm»xAddressx»a.dActivate»f.sPetronNo»e.sRemarksx");
              
                if (loValue != null){
                    if(String.valueOf(loValue.get("sRemarksx")).trim().length() != 19)
                        setMessage("Petron Value Card from the remarks of the Redemption is invalid!");
                    else if(String.valueOf(loValue.get("sPetronNo")).length() == 19)
                        setMessage("Petron Value Card was already issued!");
                    else       
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
                    ", CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')), b.sMiddName) sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", a.dActivate dTransact" +
                    ", a.sGCardNox" +
                    ", e.sRemarksx" + 
                    ", f.sPetronNo" +
                " FROM G_Card_Master a" +
                        " LEFT JOIN Client_Master b ON a.sClientID = b.sClientID" +
                        " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                        " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx" +
                    ", G_Card_Redemption e" +
                        " LEFT JOIN G_Card_Petron f ON e.sGCardNox = f.sGCardNox AND e.sRemarksx = f.sPetronNo" +   
                " WHERE a.sGCardNox = e.sGCardNox" + 
                    " AND e.sPromoIDx = 'M00114000023'" +
                    " AND f.sGCardNox IS NULL";
//              " AND e.sRemarksx <> ''" + 
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

    public void setPetronNo(String lsPetronNo){
        psPetronNo = lsPetronNo;
    }

    public String getPetronNo(){
        return psPetronNo;
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
        poData = (UnitGCard) poCtrl.openRecord(fsTransNox);

        if(poData.getGCardNo() == null){
            pnEditMode = EditMode.UNKNOWN;
        }
        else{
            //set the values of foreign key(object) to null
            pnEditMode = EditMode.READY;
        }

        poClntx = null;
        poAppxx = null;
        poBrnch = null;
        return (poData.getGCardNo() != null);

    }

    public boolean issue(){
        boolean lbCancel = false;
        boolean lbCardOk = false;

        //Validate if record is loaded
        if(pnEditMode != EditMode.READY){
            setMessage("No Record was loaded!");
            return false;
        }

        //If user presses cancel
        if(lbCancel == true){
            setMessage("Process cancelled!");
            return false;
        }
       
        poGRider.beginTrans();
      
        //issue the issue method to record this process
        lbCardOk = poCtrl.issue_petron_value_card(poData.getGCardNo(),  poTranDate, psPetronNo);

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

        setMessage("Petron Value Card issued successfully!");

        poGRider.commitTrans();
        return true;
    }

    public boolean connectCard(){
        if(!GCEncoder.init()){
            setMessage(GCEncoder.getErrMessage());         
            return false;
        }

        if(!GCEncoder.connect()){
            setMessage(GCEncoder.getErrMessage());
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
    private String psPetronNo = null;
}
