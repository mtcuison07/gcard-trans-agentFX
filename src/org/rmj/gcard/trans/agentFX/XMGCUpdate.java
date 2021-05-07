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
import java.util.Calendar;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.appdriver.constants.GCDeviceType;
import org.rmj.client.agent.XMClient;
import org.rmj.gcard.trans.GCUpdate;
import org.rmj.gcard.base.misc.GCPointBasis;
import org.rmj.gcard.device.ui.GCardDevice;
import org.rmj.gcard.device.ui.GCardDeviceFactory;
import org.rmj.gcard.service.GCRestAPI;
import org.rmj.gcard.trans.pojo.UnitGCardMD;
import org.rmj.integsys.pojo.UnitBranch;
import org.rmj.integsys.pojo.UnitGCPointsBasis;
import org.rmj.integsys.pojo.UnitGCardDetailOffline;
import org.rmj.parameters.Branch;

/**
 *
 * @author kalyptus
 */

/* Note:
 *    GRider past to this object should be set to online mode.
 */
public class XMGCUpdate {
    public XMGCUpdate(GRider foGRider, String fsBranchCd, boolean fbWithParent){
        this.poGRider = foGRider;
        if(foGRider != null){
            this.psBranchCd = fsBranchCd;
            this.pbWithParnt = fbWithParent;
            poCtrl = new GCUpdate();
            poCtrl.setGRider(foGRider);
            poCtrl.setBranch(psBranchCd);
            poCtrl.setWithParent(true);

            poData = new UnitGCardMD();
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
        return getDetail(row, poData.getOffline().get(row).getColumn(fsCol));
    }

    public Object getDetail(int row, int col){
        if(pnEditMode == EditMode.UNKNOWN || poCtrl == null)
            return null;
        else if(row < 0 || row >= poData.getOffline().size())
            return null;

        return poData.getOffline().get(row).getValue(col);
    }
   
    public int getSize(){
        if(poData == null)
            return 0;
        return poData.getOffline().size();
    }
   
    public XMClient getClient(){
        if(poClntx == null)
            poClntx = new XMClient(poGRider, psBranchCd, true);

        poClntx.openRecord(poData.getMaster().getClientID());
        return poClntx;
    }
   
    public Object getDeviceInfo(){
        if(poGCDevice == null){
            return null;
        }

        return poGCDevice;
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
   
    public boolean loadTransaction(String fsTransNox){
        poData = (UnitGCardMD) poCtrl.loadTransaction(fsTransNox);

        if(poData.getMaster().getGCardNo() == null){
            return false;
        }
        else{
            //set the values of foreign key(object) to null
            poClntx = null;

            pnEditMode = EditMode.READY;
            return true;
        }
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

            return loadTransaction((String)poGCDevice.getCardInfo("sGCardNox"));
        } else {
            System.setProperty("app.card.connected", "0");
            setMessage(poGCDevice.getMessage());
            return false;
        }        
    }

    public boolean releaseCard(){
        if(!poGCDevice.release()){
            setMessage(poGCDevice.getMessage());          
            return false;
        }

        //loadTransaction("");
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
                if(fsValue.trim().equalsIgnoreCase((String)poData.getMaster().getValue(fsFieldNm))){
                    setMessage("The same transaction code!");
                    return false;
                }
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sGCardNox LIKE " + SQLUtil.toSQL(lsPrefix + "%" + fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sCardNmbr")){
            if(pnEditMode != EditMode.UNKNOWN){
                if(fsValue.trim().equalsIgnoreCase((String)poData.getMaster().getValue(fsFieldNm))){
                    setMessage("The same card number!");
                    return false;
                }
            }
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sCardNmbr LIKE " + SQLUtil.toSQL(fsValue));
        }
        else if(fsFieldNm.equalsIgnoreCase("sClientNm")){
            if(pnEditMode != EditMode.UNKNOWN){
                String lsValue = ((String) poData.getMaster().getValue("sLastname")) + ", " +
                                    ((String) poData.getMaster().getValue("sFrstName")) + " " +
                                    ((String) poData.getMaster().getValue("sMiddName"));
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
                                                                "Name»Address»Card Number", 
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

    public boolean updateMaster(){
        JSONObject response = GCRestAPI.RequestClubMembers(poGRider, poData.getMaster().getCardNumber());

        //check result of calling the API
        if(!((String) response.get("result")).equalsIgnoreCase("success")){
            JSONObject error = (JSONObject) response.get("error");
            setMessage((String) error.get("message"));
            return false;
        }

        //get list of members
        JSONArray detail = (JSONArray) response.get("detail");
        //check if there are members that can transfer points
        JSONObject obj = (JSONObject)detail.get(0);

        if((Long) obj.get("nAvlPoint") <= 0){
            setMessage("No points was extracted from members...");
            return false;
        }

        //extracted data from web seems valid so start the operaion...
        String lsTransNo = MiscUtil.getNextCode("G_Card_Points_Transfer_Master", "sTransNox", true, poGRider.getConnection(), poGRider.getBranchCode());
        Calendar loCal = Calendar.getInstance();
        String lsDate = SQLUtil.dateFormat(loCal.getTime(), SQLUtil.FORMAT_SHORT_DATE);

        //Initialize acid transaction here...
        poGRider.beginTrans();

        String lsSQL = "INSERT INTO G_Card_Points_Transfer_Master" + 
                      " SET sTransNox = " + SQLUtil.toSQL(lsTransNo) + 
                         ", dTransact = " + SQLUtil.toSQL(lsDate) + 
                         ", sGCardNox = " + SQLUtil.toSQL(poData.getMaster().getGCardNo()) + 
                         ", sRemarksx = ''" + 
                         ", nPointsxx = NULL" + 
                         ", sOTPasswd = ''" + 
                         ", cPointSnt = '0'" + 
                         ", sModified = " + SQLUtil.toSQL(poGRider.getUserID()) + 
                         ", dModified = " + SQLUtil.toSQL(loCal.getTime());

        poGRider.executeQuery(lsSQL, "G_Card_Points_Transfer_Master", "", "");

        long total_point = 0;
        int ctr = 0;

        for (Object item : detail.toArray()) {
            JSONObject oJson = (JSONObject) item;

            lsSQL = "INSERT INTO G_Card_Points_Transfer_Detail" + 
                   " SET sTransNox = " + SQLUtil.toSQL(lsTransNo) + 
                      ", nEntryNox = " + ctr + 
                      ", sGCardNox = " + SQLUtil.toSQL((String) oJson.get("sGCardNox")) + 
                      ", nPointsxx = " + (Long) oJson.get("nPointsxx") + 
                      ", dModified = " + SQLUtil.toSQL(loCal.getTime());

            poGRider.executeQuery(lsSQL, "G_Card_Points_Transfer_Detail", "", "");
            
            total_point += (Long) oJson.get("nPointsxx");
        }
     
        String lsOTPasswd;

        if(poGCDevice.UIDeviceType() == GCardDeviceFactory.DeviceType.QRCODE){
            lsOTPasswd = String.format("%06d", MiscUtil.getRandom(999999));
        }
        else{
            lsOTPasswd = "";
        }       

        JSONObject poJson = new JSONObject();
        poJson.put("SOURCE", "TRANSFER");
        poJson.put("sOTPasswd", lsOTPasswd);

        JSONArray poArr = new JSONArray();
        JSONObject poDtl = new JSONObject();

        poDtl.put("sTransNox", "TRANSFER");
        poDtl.put("dTransact", lsDate);
        poDtl.put("sSourceNo", lsTransNo);
        poDtl.put("sSourceCD", "M00119000001"); //PTRc//Point Transfer(Recepient)
        poDtl.put("nPointsxx", total_point);

        poArr.add(poDtl);
        poJson.put("DETAIL", poArr);

        poGCDevice.setTransData(poJson);

        if(!poGCDevice.write()){
            poGRider.rollbackTrans();
            setMessage(poGCDevice.getMessage());
            return false;
        }

        response = GCRestAPI.PointTransfer(poGRider, poData.getMaster().getGCardNo(), lsTransNo, lsDate, total_point, detail);

        //check result of calling the API
        if(!((String) response.get("result")).equalsIgnoreCase("success")){
            poGRider.rollbackTrans();
            JSONObject error = (JSONObject) response.get("error");
            setMessage((String) error.get("message"));
            return false;
        }

        lsSQL = "UPDATE G_Card_Points_Transfer_Master" + 
                " SET nPointsxx = " + total_point + 
                    ", sOTPasswd = " + SQLUtil.toSQL(lsOTPasswd) + 
                    ", cPointSnt = '1'" + 
                " WHERE sTransNox = " + SQLUtil.toSQL(lsTransNo);

        poGRider.executeQuery(lsSQL, "G_Card_Points_Transfer_Master", "", "");
       
        poGRider.commitTrans();       

        return true;
    }
    
    public boolean saveUpdate(){
        String sOTPasswrd;
        
        if(poGCDevice.UIDeviceType() == GCardDeviceFactory.DeviceType.QRCODE ||
            poGCDevice.UIDeviceType() == GCardDeviceFactory.DeviceType.NONE){
            sOTPasswrd = String.format("%06d", MiscUtil.getRandom(999999));
        }
        else{
            sOTPasswrd = "";
        }
        
        JSONObject poJson = new JSONObject();
        poJson.put("SOURCE", "OFFLINE");
        poJson.put("sOTPasswd", sOTPasswrd);

        long lnPoints = 0;
        JSONArray poArr = new JSONArray();
        ArrayList<String> trans = new ArrayList<String>();
        for(UnitGCardDetailOffline loOff:poData.getOffline()){
            JSONObject poDtl = new JSONObject();
            poDtl.put("sTransNox", loOff.getTransNo());
            poDtl.put("dTransact", loOff.getTranDate());
            poDtl.put("sSourceNo", loOff.getSourceNo());
            UnitGCPointsBasis oBasis = getSource((String)loOff.getSourceCd());
            poDtl.put("sSourceCD", oBasis.getDescription());
            poDtl.put("nPointsxx", loOff.getPoints());
            
            poDtl.put("sSourcexx", loOff.getSourceCd()); //source code
            poDtl.put("nTranAmtx", loOff.getTranAmount());
            trans.add(loOff.getTransNo());
            
            lnPoints += loOff.getPoints();
            
            poArr.add(poDtl);
        }

        //mac 2019.06.24
        //  add validation:
        //      check if the transaction is empty.
        if (poArr.get(0).toString().equals("")){
            setMessage("No transaction is for writing...");
            return false;
        }
        
        poJson.put("DETAIL", poArr);
        
        if (poGCDevice.UIDeviceType() == GCardDeviceFactory.DeviceType.SMARTCARD)
            poJson.put("nPointsxx", lnPoints);
        
        poGCDevice.setTransData(poJson);

        //mac 2020-06-27
        if (poGCDevice.UIDeviceType() == GCardDeviceFactory.DeviceType.SMARTCARD){
            if(!poGCDevice.write()){
                setMessage(poGCDevice.getMessage());
                return false;
            }
        }        
        
        JSONObject response = GCRestAPI.PostOffline(
                poGRider, 
                (String) poGCDevice.getCardInfo("sCardNmbr"),
                sOTPasswrd,
                (String) poGCDevice.getCardInfo("sIMEINoxx"), 
                (String) poGCDevice.getCardInfo("sMobileNo"), 
                (String) poGCDevice.getCardInfo("dQRDateTm"),
                trans);
        String result = (String) response.get("result");
        if(result.equalsIgnoreCase("success")){
            setMessage((String) response.get("message"));            
            return true;
        }
        else{
            JSONObject error = (JSONObject) response.get("error");
            setMessage((String) error.get("message"));            
            return false;
        }
    }
   
    /*
    public boolean updateMaster(){
        String lsSQL;
        String lsTransNo;
        int lnCount;
        double lnTotPoint;
        //Get the available points for all club members...
        lsSQL = "SELECT a.sGCardNox, a.nAvlPoint, a.nTotPoint" +
                " FROM G_Card_Master a" +
                " WHERE a.cCardStat = '4'" +
                    " AND a.sGroupIDx = " + SQLUtil.toSQL(poData.getMaster().getGCardNo()) +
                    " AND a.nAvlPoint > 0" +
                    " AND a.nAvlPoint = a.nTotPoint" +
                    " AND a.cMainGrpx = '0'" +
                    " AND a.cIndvlPts = '0'";
        ResultSet loRS = poGRider.executeQuery(lsSQL);
        System.out.println(lsSQL);
      
        if(loRS == null){
            setMessage(poGRider.getErrMsg());
            return false;
        }
      
        try {
            lnTotPoint = 0;
            while(loRS.next()){
               lnTotPoint += loRS.getDouble("nAvlPoint");
            }
         
            if(lnTotPoint <= 0){
               return false;
            }
         
            poGRider.beginTrans();
         
            lsTransNo = MiscUtil.getNextCode("G_Card_Detail", "sTransNox", true, poGRider.getConnection(), psBranchCd);
            lsSQL = "INSERT INTO G_Card_Detail" +
                    " SET sTransNox = " + SQLUtil.toSQL(lsTransNo) + 
                        ", sGCardNox = " + SQLUtil.toSQL(poData.getMaster().getGCardNo()) +
                        ", sCompnyID = " + SQLUtil.toSQL(poGRider.getBranchCode()) +
                        ", dTransact = " + SQLUtil.toSQL(poGRider.getServerDate()) +
                        ", sSourceNo = ''" + 
                        ", sSourceCD = 'M00110000001'" +
                        ", nTranAmtX = " + lnTotPoint + 
                        ", nPointsxx = " + lnTotPoint +
                        ", sModified = " + SQLUtil.toSQL(poGRider.getUserID()) + 
                        ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate());
            lnCount = poGRider.executeQuery(lsSQL, "G_Card_Detail", poGRider.getBranchCode(), poGRider.getBranchCode());
            System.out.println(lsSQL);
         
            if(lnCount <= 0){
                poGRider.rollbackTrans();

                if(poGRider.getErrMsg().length() == 0)
                    setMessage("No record was updated when transferring points to the MOTHER GCard...");
                else
                    setMessage(poGRider.getErrMsg());

                return false;
            }
         
            lsSQL = "UPDATE G_Card_Master" + 
                   " SET nAvlPoint = nAvlPoint + " + lnTotPoint + 
                      ", nTotPoint = nTotPoint + " + lnTotPoint + 
                   " WHERE sGCardNox = " + SQLUtil.toSQL(poData.getMaster().getGCardNo());
            lnCount = poGRider.executeQuery(lsSQL, "G_Card_Master", poGRider.getBranchCode(), poGRider.getBranchCode());
         
            if(lnCount <= 0){
                poGRider.rollbackTrans();
                if(poGRider.getErrMsg().length() == 0)
                    setMessage("MOTHER GCard cannot be updated...");
                else
                    setMessage(poGRider.getErrMsg());

                return false;
            }

            loRS.beforeFirst();
            while(loRS.next()){
                lsSQL = "INSERT INTO G_Card_Detail_Offline" +
                        " SET sTransNox = " + SQLUtil.toSQL(MiscUtil.getNextCode("G_Card_Detail_Offline", "sTransNox", true, poGRider.getConnection(), psBranchCd)) + 
                            ", sGCardNox = " + SQLUtil.toSQL(loRS.getString("sGCardNox")) +
                            ", sCompnyID = " + SQLUtil.toSQL(poGRider.getBranchCode()) +
                            ", dTransact = " + SQLUtil.toSQL(poGRider.getServerDate()) +
                            ", sSourceNo = " + SQLUtil.toSQL(lsTransNo) +
                            ", sSourceCD = 'M00110000001'" +
                            ", nTranAmtX = " + (loRS.getDouble("nAvlPoint") * -1) + 
                            ", nPointsxx = " + (loRS.getDouble("nAvlPoint") * -1) +
                            ", cTranStat = '1'" + 
                            ", sPostedxx = " + SQLUtil.toSQL(poGRider.getUserID()) + 
                            ", dPostedxx = " + SQLUtil.toSQL(poGRider.getServerDate()) +
                            ", sModified = " + SQLUtil.toSQL(poGRider.getUserID()) + 
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate());
                lnCount = poGRider.executeQuery(lsSQL, "G_Card_Detail_Offline", poGRider.getBranchCode(), poGRider.getBranchCode());

                if(lnCount <= 0){
                    poGRider.rollbackTrans();
                    if(poGRider.getErrMsg().length() == 0)
                        setMessage("No record was updated when transferring points from the Member GCard...");
                    else
                        setMessage(poGRider.getErrMsg());

                    return false;
                }
            
                lsSQL = "UPDATE G_Card_Master" + 
                       " SET nTotPoint = nTotPoint + " + (loRS.getDouble("nAvlPoint") * -1) + 
                       " WHERE sGCardNox = " + SQLUtil.toSQL(loRS.getString("sGCardNox"));
                lnCount = poGRider.executeQuery(lsSQL, "G_Card_Master", poGRider.getBranchCode(), poGRider.getBranchCode());
                if(lnCount <= 0){
                    poGRider.rollbackTrans();
                    if(poGRider.getErrMsg().length() == 0)
                        setMessage("Member GCard cannot be updated...");                  
                    else
                        setMessage(poGRider.getErrMsg());

                    return false;
                }
            
                poGRider.commitTrans();

                Long points = (Long) GCEncoder.read(GCEncoder.POINTS);
                GCEncoder.write(GCEncoder.POINTS, (long)(points + lnTotPoint));

                Long newpoint = (Long) GCEncoder.read(GCEncoder.POINTS);
                if(((long)(points + lnTotPoint)) != newpoint){
                    setMessage("Error encoding the new Available Point the Mother GCard...");               
                    return false;
                }
            }
        } catch (SQLException ex) {
            poGRider.rollbackTrans();
            ex.printStackTrace();
            setMessage(ex.getMessage());
            return false;
        }
      
        setMessage("Tranasction saved successfully...");
        return true;
    }*/
   
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
                " WHERE a.sClientID = b.sClientID" +
                    " AND a.cCardStat = '4'";
        
                //CONCAT(b.sLastName, ', ', b.sFrstName, ' ', IF(IFNull(b.sSuffixNm, '') = '', '', CONCAT(b.sSuffixNm, ' ')), b.sMiddName)
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

    public void setGRider(GRider foGRider) {
        this.poGRider = foGRider;
        poCtrl.setGRider(foGRider);
    }
   
    public String getMessage(){return psMessage;}
    private void setMessage(String fsValue){psMessage = fsValue;}

    private UnitGCardMD poData;
    private GCUpdate poCtrl;
    private GRider poGRider;
    private GCardDevice poGCDevice;
    private int pnEditMode;
    private String psBranchCd;
    private String psMessage;

    private XMClient poClntx = null;
    private boolean pbWithParnt = false;
}
