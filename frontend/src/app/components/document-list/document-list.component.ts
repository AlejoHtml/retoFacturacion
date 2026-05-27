import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DocumentService } from '../../services/document.service';
import { ProcessedDocument } from '../../models/document.model';

@Component({
  selector: 'app-document-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './document-list.component.html',
  styleUrl: './document-list.component.scss'
})
export class DocumentListComponent implements OnInit {
  documents: ProcessedDocument[] = [];
  searchTerm: string = '';
  startDate: string = '';
  endDate: string = '';
  loading: boolean = false;
  searched: boolean = false;

  constructor(private documentService: DocumentService) {}

  ngOnInit(): void {
    this.loadDocuments();
  }

  loadDocuments(): void {
    this.loading = true;
    this.documentService.getDocuments(this.searchTerm, this.startDate, this.endDate).subscribe({
      next: (data) => {
        this.documents = data || [];
        this.loading = false;
        this.searched = true;
      },
      error: (err) => {
        console.error('Error loading documents', err);
        this.loading = false;
        this.searched = true;
      }
    });
  }

  onSearch(): void {
    if (this.startDate && this.endDate) {
      if (new Date(this.startDate) > new Date(this.endDate)) {
        alert('La fecha de inicio no puede ser mayor a la fecha de fin');
        return;
      }
    }
    this.loadDocuments();
  }

  deleteDoc(id: string): void {
    if (confirm('¿Está seguro de eliminar este documento?')) {
      this.documentService.deleteDocument(id).subscribe({
        next: () => {
          this.loadDocuments();
        },
        error: (err) => {
          console.error('Error deleting document', err);
          alert('Error al eliminar el documento');
        }
      });
    }
  }

  resendDoc(id: string): void {
    const email = prompt('Ingrese el correo de destino:');
    if (email) {
      this.documentService.resendDocument(id, email).subscribe({
        next: () => {
          alert('Correo enviado exitosamente');
        },
        error: (err) => {
          console.error('Error resending document', err);
          alert('Error al enviar el correo');
        }
      });
    }
  }

  getObjectKeys(obj: any): string[] {
    return obj ? Object.keys(obj) : [];
  }

  formatValue(key: string, value: any): string {
    if (typeof value !== 'string') return String(value);
    if (key === 'rawText' && value.length > 200) {
      return value.substring(0, 200) + '...';
    }
    // If the key looks like a date key, try to format it
    if (key.toLowerCase().includes('fecha') || key.toLowerCase().includes('date')) {
      return this.formatDate(value);
    }
    return value;
  }

  formatDate(dateStr: string): string {
    if (!dateStr || dateStr === 'Not found') return dateStr;
    try {
      const date = new Date(dateStr);
      if (isNaN(date.getTime())) return dateStr; // Return original if not a valid date
      
      const day = String(date.getDate()).padStart(2, '0');
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const year = date.getFullYear();
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      const seconds = String(date.getSeconds()).padStart(2, '0');
      
      return `${day}/${month}/${year} ${hours}:${minutes}:${seconds}`;
    } catch (e) {
      return dateStr;
    }
  }
}
