package femr.ui.controllers;

import com.google.inject.Inject;
import femr.business.dtos.CurrentUser;
import femr.business.dtos.ServiceResponse;
import femr.business.services.ISearchService;
import femr.business.services.ISessionService;
import femr.business.services.ITriageService;
import femr.common.models.*;
import femr.data.models.PatientEncounterVital;
import femr.data.models.Vital;
import femr.ui.helpers.security.AllowedRoles;
import femr.ui.helpers.security.FEMRAuthenticated;
import femr.ui.models.triage.CreateViewModelGet;
import femr.ui.models.triage.CreateViewModelPost;
import femr.ui.helpers.controller.TriageHelper;
import femr.ui.views.html.triage.index;
import femr.util.stringhelpers.StringUtils;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

import java.util.List;

@Security.Authenticated(FEMRAuthenticated.class)
@AllowedRoles({Roles.PHYSICIAN, Roles.PHARMACIST, Roles.NURSE})
public class TriageController extends Controller {

    private final Form<CreateViewModelPost> createViewModelForm = Form.form(CreateViewModelPost.class);
    private ITriageService triageService;
    private ISessionService sessionService;
    private ISearchService searchService;
    private TriageHelper triageHelper;


    @Inject
    public TriageController(ITriageService triageService, ISessionService sessionService, ISearchService searchService, TriageHelper triageHelper) {

        this.triageService = triageService;
        this.sessionService = sessionService;
        this.searchService = searchService;
        this.triageHelper = triageHelper;
    }

    public Result createGet() {
        CurrentUser currentUser = sessionService.getCurrentUserSession();

        ServiceResponse<List<? extends IVital>> vitalServiceResponse = searchService.findAllVitals();
        if (vitalServiceResponse.hasErrors()) {
            return internalServerError();
        }

        CreateViewModelGet viewModelGet = triageHelper.populateViewModelGet(null, vitalServiceResponse.getResponseObject(), false);

        return ok(index.render(currentUser, viewModelGet));
    }

    /*
    Used when user has searched for an existing patient
    and wants to create a new encounter
     */
    public Result createPopulatedGet() {
        boolean searchError = false;

        CurrentUser currentUser = sessionService.getCurrentUserSession();

        IPatient patient = null;

        //retrieve patient id from query string
        String s_id = request().getQueryString("id");
        if (StringUtils.isNullOrWhiteSpace(s_id)) {
            searchError = true;
        } else {
            s_id = s_id.trim();
            Integer id = Integer.parseInt(s_id);
            ServiceResponse<IPatient> patientServiceResponse = searchService.findPatientById(id);
            if (patientServiceResponse.hasErrors()) {
                searchError = true;
            } else {
                patient = patientServiceResponse.getResponseObject();
            }
        }

        //retrieve vitals names for dynamic html element naming
        ServiceResponse<List<? extends IVital>> vitalServiceResponse = searchService.findAllVitals();
        if (vitalServiceResponse.hasErrors()) {
            return internalServerError();
        }

        CreateViewModelGet viewModelGet = triageHelper.populateViewModelGet(patient, vitalServiceResponse.getResponseObject(), searchError);

        return ok(index.render(currentUser, viewModelGet));
    }

    /*
   *if id is 0 then it is a new patient and a new encounter
   * if id is > 0 then it is only a new encounter
    */
    public Result createPost(int id) {

        CreateViewModelPost viewModel = createViewModelForm.bindFromRequest().get();

        CurrentUser currentUser = sessionService.getCurrentUserSession();

        //save new patient if new form
        //else find the patient to create a new encounter for
        ServiceResponse<IPatient> patientServiceResponse;
        if (id == 0) {
            IPatient patient = triageHelper.populatePatient(viewModel, currentUser);
            patientServiceResponse = triageService.createPatient(patient);
        } else {
            patientServiceResponse = searchService.findPatientById(id);
            if (!StringUtils.isNullOrWhiteSpace(viewModel.getSex())) {
                patientServiceResponse = triageService.updatePatientSex(patientServiceResponse.getResponseObject().getId(), viewModel.getSex());
            }
        }
        if (patientServiceResponse.hasErrors()) {
            return internalServerError();
        }

        //create and save a new encounter
        IPatientEncounter patientEncounter = triageHelper.populatePatientEncounter(viewModel, currentUser, patientServiceResponse.getResponseObject());
        ServiceResponse<IPatientEncounter> patientEncounterServiceResponse = triageService.createPatientEncounter(patientEncounter);
        if (patientEncounterServiceResponse.hasErrors()) {
            return internalServerError();
        }

        //create and save vitals in new encounter
        if (viewModel.getRespiratoryRate() > 0) {
            createPatientEncounterVital("respiratoryRate",viewModel.getRespiratoryRate(),currentUser.getId(),patientEncounter.getId());
        }
        if (viewModel.getHeartRate() > 0) {
            createPatientEncounterVital("heartRate",viewModel.getHeartRate(),currentUser.getId(),patientEncounter.getId());
        }
        if (viewModel.getTemperature() > 0) {
            createPatientEncounterVital("temperature",viewModel.getTemperature(),currentUser.getId(),patientEncounter.getId());
        }
        if (viewModel.getOxygenSaturation() > 0) {
            createPatientEncounterVital("oxygenSaturation",viewModel.getOxygenSaturation(),currentUser.getId(),patientEncounter.getId());
        }
        if (viewModel.getHeightFeet() > 0) {
            createPatientEncounterVital("heightFeet",viewModel.getHeightFeet(),currentUser.getId(),patientEncounter.getId());
        }
        if (viewModel.getHeightInches() > 0) {
            createPatientEncounterVital("heightInches",viewModel.getHeightInches(),currentUser.getId(),patientEncounter.getId());
        }
        if (viewModel.getWeight() > 0) {
            createPatientEncounterVital("weight",viewModel.getWeight(),currentUser.getId(),patientEncounter.getId());
        }
        if (viewModel.getBloodPressureSystolic() > 0) {
            createPatientEncounterVital("bloodPressureSystolic",viewModel.getBloodPressureSystolic(),currentUser.getId(),patientEncounter.getId());
        }
        if (viewModel.getBloodPressureDiastolic() > 0) {
            createPatientEncounterVital("bloodPressureDiastolic",viewModel.getBloodPressureDiastolic(),currentUser.getId(),patientEncounter.getId());
        }
        if (viewModel.getGlucose() > 0) {
            createPatientEncounterVital("glucose",viewModel.getGlucose(),currentUser.getId(),patientEncounter.getId());
        }

        return redirect("/show?id=" + patientServiceResponse.getResponseObject().getId());
    }

    private void createPatientEncounterVital(String name, float vitalValue, int userId, int patientEncounterId){
        ServiceResponse<IVital> vitalServiceResponse = searchService.findVital(name);
        if (vitalServiceResponse.hasErrors()){
            //error
        }
        IVital vital = vitalServiceResponse.getResponseObject();
        IPatientEncounterVital patientEncounterVital = triageHelper.createPatientEncounterVital(userId, patientEncounterId, vital, vitalValue);
        ServiceResponse<IPatientEncounterVital> patientEncounterVitalServiceResponse = triageService.createPatientEncounterVital(patientEncounterVital);
        if (patientEncounterVitalServiceResponse.hasErrors()){
            //error
        }
    }
}
