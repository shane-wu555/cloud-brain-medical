import { http } from './http';

export interface Department {
  id: string;
  name: string;
  description: string;
}

export interface Doctor {
  id: string;
  name: string;
  title: string;
  departmentId: string;
  departmentName: string;
  specialty: string;
}

export interface Schedule {
  id: string;
  doctorId: string;
  doctorName: string;
  departmentId: string;
  workDate: string;
  period: string;
  capacity: number;
  booked: number;
}

export interface Appointment {
  id: string;
  scheduleId: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  departmentId: string;
  departmentName: string;
  visitDate: string;
  period: string;
  source: string;
  status: string;
  paymentStatus: string;
  paymentMethod?: string;
  triageSummary: string;
  riskLevel: string;
  recommendedDepartmentId?: string;
  queueNumber: number;
  missedCount: number;
  createdAt: string;
  paidAt?: string;
  cancelledAt?: string;
}

export interface MedicalRecord {
  id: string;
  appointmentId: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  departmentName: string;
  visitDate: string;
  period: string;
  aiTriageSummary: string;
  aiRiskLevel: string;
  chiefComplaint: string;
  presentIllness?: string;
  diagnosis: string;
  treatmentPlan: string;
  doctorRevisionNote?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  archivedAt?: string;
}

export interface ConsultationResult {
  summary: string;
  riskLevel: string;
  recommendedDepartmentId: string;
  recommendedDepartmentName: string;
  recommendedDoctors: Array<{ doctorId: string; doctorName: string; reason: string }>;
  suggestOfflineUrgent: boolean;
  recordDraft: string;
}

export async function getDepartments() {
  const { data } = await http.get<Department[]>('/departments');
  return data;
}

export async function getDoctors(departmentId?: string) {
  const { data } = await http.get<Doctor[]>('/doctors', { params: { departmentId } });
  return data;
}

export async function getSchedules(params: { doctorId?: string; departmentId?: string } = {}) {
  const { data } = await http.get<Schedule[]>('/schedules', { params });
  return data;
}

export async function createSchedule(payload: Omit<Schedule, 'id' | 'booked'>) {
  const { data } = await http.post<Schedule>('/schedules', payload);
  return data;
}

export async function getAppointments(params: { doctorId?: string; patientId?: string; status?: string } = {}) {
  const { data } = await http.get<Appointment[]>('/appointments', { params });
  return data;
}

export async function createAppointment(payload: {
  scheduleId: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  departmentId: string;
  departmentName: string;
  visitDate: string;
  period: string;
  triageSummary: string;
  riskLevel: string;
  recommendedDepartmentId?: string;
}) {
  const { data } = await http.post<Appointment>('/appointments', payload);
  return data;
}

export async function payAppointment(id: string, paymentMethod: string) {
  const { data } = await http.post<Appointment>(`/appointments/${id}/pay`, { paymentMethod });
  return data;
}

export async function cancelAppointment(id: string) {
  const { data } = await http.post<Appointment>(`/appointments/${id}/cancel`);
  return data;
}

export async function skipAppointment(id: string) {
  const { data } = await http.post<Appointment>(`/appointments/${id}/skip`);
  return data;
}

export async function updateAppointmentStatus(id: string, status: string) {
  const { data } = await http.patch<Appointment>(`/appointments/${id}/status`, { status });
  return data;
}

export async function getMedicalRecords(params: { patientId?: string; appointmentId?: string; status?: string } = {}) {
  const { data } = await http.get<MedicalRecord[]>('/medical-records', { params });
  return data;
}

export async function writeDoctorNote(payload: {
  appointmentId: string;
  chiefComplaint: string;
  presentIllness: string;
  diagnosis: string;
  treatmentPlan: string;
  doctorRevisionNote: string;
}) {
  const { data } = await http.post<MedicalRecord>('/medical-records/doctor-note', payload);
  return data;
}

export async function archiveMedicalRecord(id: string) {
  const { data } = await http.post<MedicalRecord>(`/medical-records/${id}/archive`);
  return data;
}

export async function createAiConsultation(payload: { patientId: string; description: string; symptomTags: string[] }) {
  const { data } = await http.post<ConsultationResult>('/ai/consultations', payload);
  return data;
}

export async function getDashboardOverview() {
  const { data } = await http.get<{
    todayAppointments: number;
    waitingVisits: number;
    activeDoctors: number;
    aiTriageCount: number;
    departmentLoads: Array<{ name: string; value: number }>;
  }>('/dashboard/overview');
  return data;
}
