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
import org.rmj.appdriver.iface.XMRecord;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.client.agent.XMClient;
import org.rmj.gcard.agent.misc.XMGCPartner;
import org.rmj.gcard.trans.GCard;
import org.rmj.integsys.pojo.UnitGCard;

/**
 *
 * @author kalyptus
 */
public class XMGCard implements XMRecord {
    public XMGCard(GRider foGRider, String fsBranchCd, boolean fbWithParent){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.pbWithParnt = fbWithParent;
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCard();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);
            pnEditMode = EditMode.UNKNOWN;
        }
    }

    public void setMaster(String fsCol, Object foData){
        setMaster(poData.getColumn(fsCol), foData);
    }

    public void setMaster(int fnCol, Object foData) {
        if(pnEditMode != EditMode.UNKNOWN && poCtrl != null){
            // Don't allow update for sGCardNox, cCardStat, sModified, and dModified
            if(!(fnCol == poData.getColumn("sGCardNox") ||
                fnCol == poData.getColumn("cCardStat") ||
                fnCol == poData.getColumn("sModified") ||
                fnCol == poData.getColumn("dModified"))){
                
                switch(fnCol){
                case  5: //dMemberxx
                case  9: //dActivate
                case 10: //dCardExpr
                case 14: //dLastRedm
                    if(foData instanceof Date)
                        poData.setValue(fnCol, foData);
                    else
                        poData.setValue(fnCol, null);
                    break;
                case 11: //nPointsxx
                case 12: //nTotPoint
                case 13: //nAvlPoint
                    if(foData instanceof Number)
                        poData.setValue(fnCol, foData);
                    else
                        poData.setValue(fnCol, 0.00);
                    break;
                default:
                    poData.setValue(fnCol, foData);
                }
            }
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

    public boolean newRecord() {
        if(poCtrl == null){
            return false;
        }

        poData = (UnitGCard) poCtrl.newRecord();

        if(poData == null){
            return false;
        }
        else{
            //set the values of foreign key(object) to null
            poPartn = null;
            poClntx = null;

            pnEditMode = EditMode.ADDNEW;
            return true;
        }
    }

    public boolean openRecord(String fstransNox) {
        if(poCtrl == null){
            return false;
        }

        poData = (UnitGCard) poCtrl.openRecord(fstransNox);

        if(poData.getCardNumber() == null){
            setMessage("G-Card number is null...");
            return false;
        }
        else{
            //set the values of foreign key(object) to null
            poPartn = null;
            poClntx = null;

            pnEditMode = EditMode.READY;
            return true;
        }
    }

    public boolean updateRecord() {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY) {
            return false;
        }
        else{
            pnEditMode = EditMode.UPDATE;
            return true;
        }
    }

    public boolean saveRecord() {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode == EditMode.UNKNOWN){
            return false;
        }
        else{
            UnitGCard loResult;
         
            if(!pbWithParnt) poGRider.beginTrans();

            if(pnEditMode == EditMode.ADDNEW)
                loResult = (UnitGCard) poCtrl.saveRecord(poData, "");
            else
                loResult = (UnitGCard) poCtrl.saveRecord(poData, (String) poData.getValue(1));

            if(loResult == null){
                if(!pbWithParnt) poGRider.rollbackTrans();
                return false;
            }   
            else{
                pnEditMode = EditMode.READY;
                poData = loResult;
                if(!pbWithParnt) poGRider.commitTrans();
                
                setMessage("Transaction saved successfully...");
                return true;
            }
        }
    }

    public boolean deleteRecord(String string) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean deactivateRecord(String string) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean activateRecord(String string) {
        throw new UnsupportedOperationException("Not supported yet.");
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
            
            //sSQL = MiscUtil.addCondition(lsSQL, "CONCAT(b.sLastName, ', ', b.sFrstName, IF(IFNULL(b.sSuffixNm, '') = '', CONCAT(' ', b.sSuffixNm), ' '), b.sMiddName) LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sCardNmbr")){
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sCardNmbr LIKE " + SQLUtil.toSQL(fsValue));
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
                                                                "Card Number»Name»Address»Activated", 
                                                                "a.sCardNmbr»sClientNm»xAddressx»dTransact");
              
                if (loValue != null){
                    lbHasRec = openRecord((String) loValue.get("sGCardNox"));
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
            if(!pbWithParnt)
                MiscUtil.close(loCon);
        }

        return lbHasRec;
    }

    public String getSearchSQL(){
        return "SELECT" +
                    "  a.sApplicNo" +
                    ", a.sCardNmbr" +
                    ", b.sCompnyNm sClientNm" +
                    ", CONCAT(b.sAddressx, ', ', c.sTownName, ' ', d.sProvName, ' ', c.sZippCode) xAddressx" +
                    ", a.dActivate dTransact" +
                    ", a.sGCardNox" +
                    ", a.sLastLine" + 
                " FROM G_Card_Master a" +
                    " LEFT JOIN Client_Master b ON a.sClientID = b.sClientID" +
                    " LEFT JOIN TownCity c ON b.sTownIDxx = c.sTownIDxx" +
                    " LEFT JOIN Province d ON c.sProvIDxx = d.sProvIDxx";
        
        //CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')), b.sMiddName)
    }

    public void setBranch(String fsBranchCd) {
        psBranchCd = fsBranchCd;
        poCtrl.setBranch(psBranchCd);
    }

    public int getEditMode() {
        return pnEditMode;
    }

    public void setGRider(GRider foGRider) {
        this.poGRider = foGRider;
        poCtrl.setGRider(foGRider);
    }

    public XMClient getClient(){
        if(poClntx == null)
            poClntx = new XMClient(poGRider, psBranchCd, true);

        poClntx.openRecord(poData.getClientID());
        return poClntx;
    }

    public XMGCPartner getCompany(){
        if(poPartn == null)
            poPartn = new XMGCPartner(poGRider, psBranchCd, true);

        poPartn.openRecord(poData.getCompanyID());
        return poPartn;
    }

   
    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}
    
    private UnitGCard poData;
    private GCard poCtrl;
    private GRider poGRider;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;
    private boolean pbWithParnt = false;

    private XMGCPartner poPartn = null;
    private XMClient poClntx = null;
}
