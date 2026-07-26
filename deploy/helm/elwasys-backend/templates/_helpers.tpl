{{/*
Standard-Helm-Chart-Helpers (Name/Fullname/Labels/ServiceAccount-Name) - unverändertes
Boilerplate-Muster (siehe "helm create"-Vorlage), keine elwasys-spezifische Logik.
*/}}

{{- define "elwasys-backend.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "elwasys-backend.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "elwasys-backend.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "elwasys-backend.labels" -}}
helm.sh/chart: {{ include "elwasys-backend.chart" . }}
{{ include "elwasys-backend.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "elwasys-backend.selectorLabels" -}}
app.kubernetes.io/name: {{ include "elwasys-backend.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "elwasys-backend.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "elwasys-backend.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/*
Effektive JDBC-URL: database.jdbcUrlOverride hat Vorrang, sonst aus host/port/name
zusammengesetzt (siehe values.yaml "database:"-Kommentar).
*/}}
{{- define "elwasys-backend.jdbcUrl" -}}
{{- if .Values.database.jdbcUrlOverride -}}
{{- .Values.database.jdbcUrlOverride -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:%v/%s" .Values.database.host .Values.database.port .Values.database.name -}}
{{- end -}}
{{- end -}}

{{/*
Effektiver Image-Tag mit Guard (Issue #89): image.tag hat Vorrang, sonst Chart.appVersion.
Steht der so ermittelte Tag auf dem Entwicklungs-Sentinel "0.0.0-local-development" (der
Default in Chart.yaml, den der Release-Workflow auf die Release-Version hebt), existiert KEIN
solches Image in GHCR - ein "helm install" ohne "--set image.tag=<release>" würde also ein
nicht existierendes Image ziehen. "fail" bricht das Rendern dann mit klarer Meldung ab, statt
still einen kaputten Deploy zu erzeugen.
*/}}
{{- define "elwasys-backend.imageTag" -}}
{{- $tag := .Values.image.tag | default .Chart.AppVersion -}}
{{- if eq $tag "0.0.0-local-development" -}}
{{- fail "image.tag ist nicht gesetzt und Chart.appVersion steht auf dem Entwicklungs-Sentinel 0.0.0-local-development (kein in GHCR existierendes Image). Setze image.tag (--set image.tag=<release>, ideal ein @sha256:-Digest) oder nutze einen Chart-Stand aus einem echten Release - dort hebt der Release-Workflow appVersion mit. Siehe deploy/helm/elwasys-backend/values.yaml image.tag." -}}
{{- end -}}
{{- $tag -}}
{{- end -}}

{{/*
Name des DB-Secrets: existingSecret hat Vorrang vor dem selbst erzeugten Secret (siehe
templates/secret.yaml).
*/}}
{{- define "elwasys-backend.dbSecretName" -}}
{{- if .Values.database.existingSecret -}}
{{- .Values.database.existingSecret -}}
{{- else -}}
{{- printf "%s-db" (include "elwasys-backend.fullname" .) -}}
{{- end -}}
{{- end -}}
