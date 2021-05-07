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
import org.json.simple.JSONObject;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.iface.XMRecord;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.gcard.trans.GCardSpareparts;
import org.rmj.gcard.trans.pojo.UnitGCardSpareparts;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author kalyptus
 */
public class XMGCardSpareparts implements XMRecord{
    public XMGCardSpareparts(GRider foGRider, String fsBranchCd, boolean fbWithParent){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.pbWithParnt = fbWithParent;
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCardSpareparts();
            poCtrl.setGRider(this.poGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);
            pnEditMode = EditMode.UNKNOWN;
        }
    }

    public void setMaster(String fsCol, Object foData){
        setMaster(poData.getColumn(fsCol), foData);
    }

    public void setMaster(int fnCol, Object foData) {
        if(pnEditMode != EditMode.UNKNOWN){
            if(!(fnCol == poData.getColumn("sPartsIDx") ||
                fnCol == poData.getColumn("cPartType") ||
                fnCol == poData.getColumn("sModified") ||
                fnCol == poData.getColumn("dModified"))) {

                poData.setValue(fnCol, foData);
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

        poData = poCtrl.newRecord();

        if(poData == null){
            return false;
        }
        else{
            pnEditMode = EditMode.ADDNEW;
            return true;
        }
    }

    public boolean openRecord(String fstransNox) {
        if(poCtrl == null){
            return false;
        }

        poData = poCtrl.openRecord(fstransNox);
        if(poData.getPartsID() == null){
            return false;
        }
        else{
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
            UnitGCardSpareparts loResult;

            if(!pbWithParnt) poGRider.beginTrans();

            if(pnEditMode == EditMode.ADDNEW)
                loResult = poCtrl.saveRecord(poData, "");
            else
                loResult = poCtrl.saveRecord(poData, (String) poData.getValue(1));

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

    public boolean deleteRecord(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if(!pbWithParnt) poGRider.beginTrans();

            boolean lbResult = poCtrl.deleteRecord(fsTransNox);
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if(!pbWithParnt) poGRider.commitTrans();
            }
            else
                if(!pbWithParnt) poGRider.rollbackTrans();
             
            return lbResult;
        }
    }

    public boolean deactivateRecord(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if(!pbWithParnt) poGRider.beginTrans();

            boolean lbResult = poCtrl.deactivateRecord(fsTransNox);
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if(!pbWithParnt) poGRider.commitTrans();
            }
            else
                if(!pbWithParnt) poGRider.rollbackTrans();

            return lbResult;
        }  
    }

    public boolean activateRecord(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if(!pbWithParnt) poGRider.beginTrans();

            boolean lbResult = poCtrl.activateRecord(fsTransNox);

            if(lbResult){
               pnEditMode = EditMode.UNKNOWN;
               if(!pbWithParnt) poGRider.commitTrans();
            }
            else
               if(!pbWithParnt) poGRider.rollbackTrans();

            return lbResult;
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
        if(fsFieldNm.equalsIgnoreCase("sPartsIDx")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
                setMessage("The same parts id!");            
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sPartsIDx LIKE " + SQLUtil.toSQL( fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sBarrcode")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
               setMessage("The same barcode!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sBarrcode LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sDescript")){
            if(fsValue.trim().equalsIgnoreCase((String)poData.getValue(fsFieldNm))){
               setMessage("The same Descrption!");
                return false;
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sDescript LIKE " + SQLUtil.toSQL(fsValue + "%"));
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
                                                                "Bar code»Description", 
                                                                "sBarrcode»sDescript");
              
                if (loValue != null){
                    lbHasRec = openRecord((String) loValue.get("sPartsIDx"));
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
                    "  sPartsIDx" +
                    ", sBarrcode" +
                    ", sDescript" +
                " FROM Spareparts" ; 
    }
   
    public void setBranch(String foBranchCD) {
        psBranchCd = foBranchCD;
        poCtrl.setBranch(foBranchCD);
    }

    public int getEditMode() {
        return pnEditMode;
    }

    // Added methods here
    public void setGRider(GRider foGRider) {
        this.poGRider = foGRider;
        poCtrl.setGRider(foGRider);
    }
   
    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}

    // Member Variables here
    private boolean pbWithParnt;
    private UnitGCardSpareparts poData;
    private GCardSpareparts poCtrl;
    private GRider poGRider;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;
}
