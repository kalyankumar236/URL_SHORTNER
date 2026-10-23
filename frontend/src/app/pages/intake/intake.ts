import { Component, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { CreateWorkflowRequest } from '../../core/models';

@Component({
  selector: 'app-intake',
  imports: [ReactiveFormsModule],
  templateUrl: './intake.html',
})
export class Intake {
  private fb = inject(FormBuilder);
  private api = inject(ApiService);
  private router = inject(Router);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.group({
    requirement: ['', [Validators.required, Validators.minLength(10)]],
    runNow: [true],
    inputs: this.fb.array<ReturnType<Intake['newInput']>>([]),
  });

  get inputs(): FormArray {
    return this.form.get('inputs') as FormArray;
  }

  private newInput(key = '', value = '') {
    return this.fb.group({
      key: [key, Validators.required],
      value: [value],
    });
  }

  protected addInput(): void {
    this.inputs.push(this.newInput());
  }

  protected removeInput(i: number): void {
    this.inputs.removeAt(i);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    const taskInputs: Record<string, string> = {};
    for (const ctrl of this.inputs.controls) {
      const key = (ctrl.get('key')?.value ?? '').toString().trim();
      if (key) {
        taskInputs[key] = (ctrl.get('value')?.value ?? '').toString();
      }
    }

    const body: CreateWorkflowRequest = {
      requirement: this.form.value.requirement!.trim(),
      taskInputs: Object.keys(taskInputs).length ? taskInputs : undefined,
    };
    const runNow = this.form.value.runNow;

    this.api.createWorkflow(body).subscribe({
      next: (wf) => {
        if (runNow) {
          this.api.runWorkflow(wf.id).subscribe({
            next: () => this.router.navigate(['/workflows', wf.id]),
            error: () => this.router.navigate(['/workflows', wf.id]),
          });
        } else {
          this.router.navigate(['/workflows', wf.id]);
        }
      },
      error: (e) => {
        this.submitting.set(false);
        this.error.set(
          e?.status === 401 || e?.status === 403
            ? 'Not authorized. Set admin credentials in the header (demo: admin/admin).'
            : 'Failed to create workflow. Check the backend and try again.',
        );
      },
    });
  }
}
