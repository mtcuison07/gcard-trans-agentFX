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
import java.util.Date;
import org.json.simple.JSONObject;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.gcard.trans.GCRedemptionPromo;
import org.rmj.gcard.trans.pojo.UnitGCardPromo;
import org.rmj.integsys.pojo.UnitGCardPromoDetail;
import org.rmj.parameters.agent.XMBranch;

/**
 *
 * @author kalyptus
 */
public class XMGCRedemptionPromo {
    public XMGCRedemptionPromo(GRider foGRider, String fsBranchCd, boolean fbWithParent){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.pbWithParnt = fbWithParent;
            this.psBranchCd = fsBranchCd;
            poCtrl = new GCRedemptionPromo();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);
            pnEditMode = EditMode.UNKNOWN;
        }
    }

    public void setMaster(String fsCol, Object foData){
        setMaster(poData.getMaster().getColumn(fsCol), foData);
    }

    public void setMaster(int fnCol, Object foData) {
        if(pnEditMode != EditMode.UNKNOWN && poCtrl != null){
          // Don't allow update for sTransNox, cTranStat, sModified, and dModified
            if(!(fnCol == poData.getMaster().getColumn("sTransNox") ||
                fnCol == poData.getMaster().getColumn("cTranStat") ||
                fnCol == poData.getMaster().getColumn("sModified") ||
                fnCol == poData.getMaster().getColumn("dModified"))){

                if(fnCol == poData.getMaster().getColumn("dDateFrom") || 
                    fnCol == poData.getMaster().getColumn("dDateThru")){
                    if(foData instanceof Date)
                        poData.getMaster().setValue(fnCol, foData);
                    else
                    poData.getMaster().setValue(fnCol, null);
                }
                else{
                    poData.getMaster().setValue(fnCol, foData);
                }
            }
        }
    }

    public Object getMaster(String fsCol){
        return getMaster(poData.getMaster().getColumn(fsCol));
    }
   
    public Object getMaster(int fnCol) {
        if(pnEditMode == EditMode.UNKNOWN || poCtrl == null)
            return null;
        else{
            return poData.getMaster().getValue(fnCol);
        }
    }

    public Object getDetail(int row, String fsCol){
        return getDetail(row, poData.getDetail().get(row).getColumn(fsCol));
    }

    public Object getDetail(int row, int col){
        if(pnEditMode == EditMode.UNKNOWN || poCtrl == null)
            return null;
        else if(row < 0 || row >= poData.getDetail().size())
            return null;
      
        return poData.getDetail().get(row).getValue(col);
    }   

    public void setDetail(int row, String fsCol, Object value){
        setDetail(row, poData.getDetail().get(row).getColumn(fsCol), value);
    }

    public void setDetail(int row, int col, Object value){
        if(pnEditMode != EditMode.UNKNOWN && poCtrl != null){
            if(row >= 0 && row <= poData.getDetail().size()){
                if(row == poData.getDetail().size()){
                    poData.getDetail().add(new UnitGCardPromoDetail());
                    poParts.add(null);
                }
             
                poData.getDetail().get(row).setValue(col, value);
            }
        }
    }   
   
    public void addDetail(){
        if(pnEditMode != EditMode.UNKNOWN && poCtrl != null){
            poData.getDetail().add(new UnitGCardPromoDetail());
            poParts.add(null);
        }
    }
   
    public void deleteDetail(int row){
        if(pnEditMode != EditMode.UNKNOWN && poCtrl != null){
            if(!(row < 0 || row >= poData.getDetail().size()))
                poData.getDetail().remove(row);
        }
    }
   
    public boolean newTransaction() {
        poParts.clear();

        if(poCtrl == null){
            return false;
        }

        poData = (UnitGCardPromo) poCtrl.newTransaction();

        if(poData == null){
            return false;
        }
        else{
            //set the values of foreign key(object) to null
            poBranch = null;
            pnEditMode = EditMode.ADDNEW;
            return true;
        }
    }

    public boolean loadTransaction(String fsTransNox) {
        poParts.clear();

        if(poCtrl == null){
            return false;
        }

        poData = (UnitGCardPromo) poCtrl.loadTransaction(fsTransNox);

        if(poData.getMaster().getTransNo() == null){
            return false;
        }
        else{
            //set the values of foreign key(object) to null
            poBranch = null;
         
            for(int lnCtr=0;lnCtr<=poData.getDetail().size();lnCtr++)
                poParts.add(null);
         
            pnEditMode = EditMode.READY;
            return true;
        }
    }

    public boolean saveUpdate() {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode == EditMode.UNKNOWN){
            return false;
        }
        else{
            if(!pbWithParnt) poGRider.beginTrans();
          
            UnitGCardPromo loResult=null;
            if(pnEditMode == EditMode.ADDNEW)
                loResult = (UnitGCardPromo) poCtrl.saveUpdate(poData, "");
            else
                loResult = (UnitGCardPromo) poCtrl.saveUpdate(poData, (String) poData.getMaster().getValue(1));

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

    public boolean deleteTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if(!pbWithParnt) poGRider.beginTrans();
 
            boolean lbResult = poCtrl.deleteTransaction(fsTransNox);
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if(!pbWithParnt) poGRider.commitTrans();
            }
            else
            if(!pbWithParnt) poGRider.rollbackTrans();

            return lbResult;
        }
    }

    public boolean closeTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            setMessage("Edit mode does not allow verification of transaction!");         
            return false;
        }
        else{
            boolean lbResult = poCtrl.closeTransaction(fsTransNox);
            if(lbResult){
                setMessage("Transaction verified successfully!");
                pnEditMode = EditMode.UNKNOWN;
            }
            else
                setMessage(poCtrl.getErrMsg() + poCtrl.getMessage());

            return lbResult;
        }
    }

    public boolean postTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if(!pbWithParnt) poGRider.beginTrans();

            boolean lbResult = poCtrl.postTransaction(fsTransNox);
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if(!pbWithParnt) poGRider.commitTrans();
            }
            else
                if(!pbWithParnt) poGRider.rollbackTrans();
             
            return lbResult;
        }
    }

    public boolean voidTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if(!pbWithParnt) poGRider.beginTrans();

            boolean lbResult = poCtrl.voidTransaction(fsTransNox);
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if(!pbWithParnt) poGRider.commitTrans();
            }
            else
                if(!pbWithParnt) poGRider.rollbackTrans();
             
            return lbResult;
        }
    }

    public boolean cancelTransaction(String fsTransNox) {
        if(poCtrl == null){
            return false;
        }
        else if(pnEditMode != EditMode.READY){
            return false;
        }
        else{
            if(!pbWithParnt) poGRider.beginTrans();

            boolean lbResult = poCtrl.cancelTransaction(fsTransNox);
            if(lbResult){
                pnEditMode = EditMode.UNKNOWN;
                if(!pbWithParnt) poGRider.rollbackTrans();
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

        String lsSQL = getSQL_Master();
        if(fsFieldNm.equalsIgnoreCase("sTransNox")){
            String lsPrefix = "";
            if(fsValue.trim().length() <= 0 || fsValue.contains("%"))
                lsPrefix = "";
            else if(fsValue.length() <= 6)
                lsPrefix = psBranchCd + SQLUtil.dateFormat(poGRider.getSysDate(), "yy");
            else if(fsValue.length() <= 8)
                lsPrefix = psBranchCd;
            if(pnEditMode != EditMode.UNKNOWN){
                if(fsValue.trim().equalsIgnoreCase((String)poData.getMaster().getValue(fsFieldNm))){
                    setMessage("The same transaction code!");
                    return false;
                }
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sTransNox LIKE " + SQLUtil.toSQL(lsPrefix + "%" + fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sPromCode")){
            String []laNames = MiscUtil.splitName(fsValue);
            if(pnEditMode != EditMode.UNKNOWN){
                if(fsValue.trim().equalsIgnoreCase((String)poData.getMaster().getValue(fsFieldNm))){
                    setMessage("The same Promo Code detected!");
                    return false;
                }
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sPromCode LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sPromDesc")){
            if(pnEditMode != EditMode.UNKNOWN){
                if(fsValue.trim().equalsIgnoreCase((String)poData.getMaster().getValue(fsFieldNm))){
                    setMessage("The same Promo Description detected!");
                    return false;
                }
            }

            lsSQL = MiscUtil.addCondition(lsSQL, "sPromDesc LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        if(!fsFilter.isEmpty()){
            lsSQL = MiscUtil.addCondition(lsSQL, fsFilter);
        }

        System.out.println(lsSQL);

        //Create the connection object
        Connection loCon = poGRider.getConnection();

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

            loStmt = loCon.createStatement();
            loRS = loStmt.executeQuery(lsSQL);

            if(!loRS.next())
                setMessage("No Record Found!");
            else{
                
                
                        
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                        "Trans. No»Description»Code»Promo From»Promo Thru", 
                                                        "sTransNox»sPromDesc»sPromCode»dDateFrom»dDateThru");
              
                if (loValue != null){
                    lbHasRec = loadTransaction((String) loValue.get("sTransNox"));
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

    public boolean searchField(int row, String field, String value){
        if(field.equalsIgnoreCase("sBarrCode")){
            return searchParts(row, field, value);
        }
        else if(field.equalsIgnoreCase("sDescript")){
            return searchParts(row, field, value);
        }
        else{
            setMessage("Invalid search field [" + field + "]  detected!");
            return false;
        }
    }

    private boolean searchParts(int row, String fsFieldNm, String fsValue){
        System.out.println("Inside searchParts");
        fsValue = fsValue.trim();
        
        if(fsValue.trim().length() == 0){
            setMessage("Nothing to process!");
            return false;
        }

        String lsSQL = getSQL_Parts();
        if(fsFieldNm.equalsIgnoreCase("sBarrCode")){
            if(getSpareparts(row) != null){  
                if(fsValue.trim().equalsIgnoreCase((String)getSpareparts(row).getMaster("sBarrCode"))){
                    setMessage("The same Barcode!");
                    return false;
                }
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sBarrCode LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
        else if(fsFieldNm.equalsIgnoreCase("sDescript"))
        {
            if(getSpareparts(row) != null){  
                if(fsValue.trim().equalsIgnoreCase((String)getSpareparts(row).getMaster("sDescript"))){
                    setMessage("The same Description Code!");
                    return false;
                }
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "sDescript LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }
      
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
            System.out.println(lsSQL);
            loRS = loStmt.executeQuery(lsSQL);

            if(loRS.next()){
                JSONObject loValue = showFXDialog.jsonBrowse(poGRider, loRS, 
                                                                "Bar code»Description»Parts ID", 
                                                                "sBarrCode»sDescript»sPartsIDx");
              
                if (loValue != null){
                    poData.getDetail().get(row).setPartsID((String) loValue.get("sPartsIDx"));
                    //poData.getDetail().get(row).setTranAmount(loRS.getDouble("nSelPrice"));
                    poParts.set(row, new XMGCardSpareparts(poGRider, psBranchCd, true)); 
                    poParts.get(row).openRecord((String) loValue.get("sPartsIDx"));
                    
                    //mac 2019.07.01
                    //  added replacement of:
                    //      poData.getDetail().get(row).setTranAmount(loRS.getDouble("nSelPrice"));
                    poData.getDetail().get(row).setTranAmount((Double)poParts.get(row).getMaster("nSelPrice"));
                    lbHasRec = true;
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
   
    public XMBranch getBranch(){
        if(poBranch == null)
            poBranch = new XMBranch(poGRider, psBranchCd, true);

        poBranch.openRecord(psBranchCd);
        return poBranch;
    }

    public void setBranch(String fsBranchCD) {
        psBranchCd = fsBranchCD;
        poCtrl.setBranch(fsBranchCD);
    }

    public void setGRider(GRider foGRider) {
        this.poGRider = foGRider;
        poCtrl.setGRider(foGRider);
    }

    public int getEditMode() {
        return pnEditMode;
    }

    private String getSQL_Master(){        
        return "SELECT" +
                    "  sTransNox" +
                    ", sPromCode" +
                    ", sPromDesc" +
                    ", dDateFrom" +
                    ", dDateThru" +
                " FROM G_Card_Promo_Master"; 
   }

    private String getSQL_Branch(){
        return "SELECT" +
                    "  sBranchCd" +
                    ", sBranchNm" +
                " FROM Branch" +
                " WHERE cRecdStat = '1'";
    }

    private String getSQL_Parts(){
        return "SELECT" +
                    "  sPartsIDx" +
                    ", sBarrCode" +
                    ", sDescript" +
                " FROM Spareparts" +
                " WHERE cRecdStat = '1'";
    }
   
    public XMGCardSpareparts getSpareparts(int row){
        if(pnEditMode == EditMode.UNKNOWN || poCtrl == null)
            return null;
        else if(row < 0 || row >= poData.getDetail().size())
            return null;
      
        if(poParts.get(row) == null)
            poParts.set(row, new XMGCardSpareparts(poGRider, psBranchCd, true));

        poParts.get(row).openRecord(poData.getDetail().get(row).getPartsID());
        return poParts.get(row);
    }   
   
    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}
   
    ArrayList<XMGCardSpareparts> poParts = new ArrayList<XMGCardSpareparts>();
    private XMBranch poBranch = null;

    private UnitGCardPromo poData;

    private GCRedemptionPromo poCtrl;
    private GRider poGRider;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;
    private boolean pbWithParnt = false;
}
