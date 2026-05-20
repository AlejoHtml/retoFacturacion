import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProcessedDocument } from '../models/document.model';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {
  private apiUrl = 'http://localhost:8082/api/documents';

  constructor(private http: HttpClient) { }

  getDocuments(filters: any = {}): Observable<ProcessedDocument[]> {
    let params = new HttpParams();
    
    if (typeof filters === 'string') {
      params = params.set('search', filters);
    } else {
      if (filters.invoiceNumber) params = params.set('invoiceNumber', filters.invoiceNumber);
      if (filters.sender) params = params.set('sender', filters.sender);
      if (filters.date) params = params.set('date', filters.date);
      if (filters.search) params = params.set('search', filters.search);
    }

    return this.http.get<ProcessedDocument[]>(this.apiUrl, { params });
  }

  deleteDocument(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  resendDocument(id: string, targetEmail: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/resend`, targetEmail);
  }
}