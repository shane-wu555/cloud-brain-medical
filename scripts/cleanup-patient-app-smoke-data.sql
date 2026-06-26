begin;

delete from pharmacy.inventory_flow
where prescription_id in (
    'rx-test-dispensed-001',
    'rx-test-pending-001',
    'rx-test-waiting-001'
);

delete from pharmacy.prescription_item
where prescription_id in (
    'rx-test-dispensed-001',
    'rx-test-pending-001',
    'rx-test-waiting-001'
);

delete from pharmacy.prescription
where id in (
    'rx-test-dispensed-001',
    'rx-test-pending-001',
    'rx-test-waiting-001'
);

delete from medical_record.medical_record_report_link
where medical_record_id = 'record-test-history-001'
   or medical_order_id in (
       'order-test-check-report-001',
       'order-test-lab-report-001'
   );

delete from medical_order.medical_report
where medical_order_id in (
    'order-test-check-report-001',
    'order-test-lab-report-001',
    'order-test-disposal-done-001',
    'order-test-disposal-wait-001',
    'order-test-check-unpaid-001',
    'order-test-lab-unpaid-001',
    'order-test-disposal-unpaid-001'
);

delete from medical_order.medical_attachment
where medical_order_id in (
    'order-test-check-report-001',
    'order-test-lab-report-001',
    'order-test-disposal-done-001',
    'order-test-disposal-wait-001',
    'order-test-check-unpaid-001',
    'order-test-lab-unpaid-001',
    'order-test-disposal-unpaid-001'
);

delete from medical_order.ai_medical_task
where medical_order_id in (
    'order-test-check-report-001',
    'order-test-lab-report-001',
    'order-test-disposal-done-001',
    'order-test-disposal-wait-001',
    'order-test-check-unpaid-001',
    'order-test-lab-unpaid-001',
    'order-test-disposal-unpaid-001'
);

delete from medical_order.medical_order
where id in (
    'order-test-check-report-001',
    'order-test-lab-report-001',
    'order-test-disposal-done-001',
    'order-test-disposal-wait-001',
    'order-test-check-unpaid-001',
    'order-test-lab-unpaid-001',
    'order-test-disposal-unpaid-001'
);

delete from medical_record.medical_record_access_log
where medical_record_id = 'record-test-history-001';

delete from medical_record.medical_record_version
where medical_record_id = 'record-test-history-001';

delete from medical_record.medical_record
where appointment_id in (
    'appt-test-future-001',
    'appt-test-future-002',
    'appt-test-history-001',
    'appt-test-pending-payment-001'
);

delete from cashier.refund_order
where business_id in (
    'appt-test-future-001',
    'appt-test-future-002',
    'appt-test-history-001',
    'appt-test-pending-payment-001',
    'order-test-check-report-001',
    'order-test-lab-report-001',
    'order-test-disposal-done-001',
    'order-test-disposal-wait-001',
    'order-test-check-unpaid-001',
    'order-test-lab-unpaid-001',
    'order-test-disposal-unpaid-001',
    'rx-test-dispensed-001',
    'rx-test-pending-001',
    'rx-test-waiting-001'
);

delete from cashier.payment_order
where business_id in (
    'appt-test-future-001',
    'appt-test-future-002',
    'appt-test-history-001',
    'appt-test-pending-payment-001',
    'order-test-check-report-001',
    'order-test-lab-report-001',
    'order-test-disposal-done-001',
    'order-test-disposal-wait-001',
    'order-test-check-unpaid-001',
    'order-test-lab-unpaid-001',
    'order-test-disposal-unpaid-001',
    'rx-test-dispensed-001',
    'rx-test-pending-001',
    'rx-test-waiting-001'
);

delete from appointment.integration_event
where aggregate_id in (
    'appt-test-future-001',
    'appt-test-future-002',
    'appt-test-history-001',
    'appt-test-pending-payment-001'
);

delete from appointment.appointment
where id in (
    'appt-test-future-001',
    'appt-test-future-002',
    'appt-test-history-001',
    'appt-test-pending-payment-001'
);

delete from appointment.slot_inventory
where schedule_id in ('schedule-001', 'schedule-002', 'schedule-003', 'schedule-004');

delete from appointment.slot_inventory
where schedule_id like 'schedule-test-%'
   or schedule_id like 'schedule-002-%';

delete from doctor.doctor_schedule_time_slot
where schedule_id in (
    'schedule-test-general-001',
    'schedule-test-general-002-am',
    'schedule-test-general-002-pm',
    'schedule-test-general-003-full',
    'schedule-test-neuro-001',
    'schedule-test-neuro-002-am',
    'schedule-test-neuro-002-pm',
    'schedule-test-neuro-003-am',
    'schedule-test-neuro-history-001',
    'schedule-test-rehab-001-am',
    'schedule-test-rehab-001-pm'
);

delete from doctor.doctor_schedule
where id in (
    'schedule-test-general-001',
    'schedule-test-general-002-am',
    'schedule-test-general-002-pm',
    'schedule-test-general-003-full',
    'schedule-test-neuro-001',
    'schedule-test-neuro-002-am',
    'schedule-test-neuro-002-pm',
    'schedule-test-neuro-003-am',
    'schedule-test-neuro-history-001',
    'schedule-test-rehab-001-am',
    'schedule-test-rehab-001-pm'
);

delete from doctor.doctor
where id in (
    'doctor-test-neuro-002',
    'doctor-test-neuro-003',
    'doctor-test-general-002',
    'doctor-test-general-003',
    'doctor-test-rehab-001'
);

delete from doctor.department
where id = 'dept-rehab';

delete from patient.account_patient_binding
where account_id in (
    'patient-test-verified-001',
    'patient-test-unverified-001'
);

delete from patient.patient_profile
where id in (
    'patient-profile-test-self-001',
    'patient-profile-test-family-001',
    'patient-test-verified-001',
    'patient-test-unverified-001'
)
or user_id in (
    'patient-profile-test-self-001',
    'patient-profile-test-family-001',
    'patient-test-verified-001',
    'patient-test-unverified-001'
);

delete from auth.user_account
where id in (
    'patient-test-verified-001',
    'patient-test-unverified-001',
    'doctor-test-neuro-002',
    'doctor-test-neuro-003',
    'doctor-test-general-002',
    'doctor-test-general-003',
    'doctor-test-rehab-001'
);

commit;
